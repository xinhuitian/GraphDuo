/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.{Graph, TripletFields, VertexRDD}
import org.apache.spark.graphx.impl.{EdgeActiveness, GraphImpl}
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by sunny on 4/25/16.
 */
class MyGraphImpl[VD: ClassTag, ED: ClassTag] protected(
    @transient val vertices: MyVertexRDD[VD, ED])
  extends MyGraph[VD, ED] with Serializable with Logging {

  override def getActiveNums: Long = vertices.getActiveNums

  override def cache(): MyGraph[VD, ED] = {
    vertices.cache ()
    this
  }

  override def persist(newLevel: StorageLevel): MyGraph[VD, ED] = {
    vertices.persist (newLevel)
    this
  }

  override def unpersist(blocking: Boolean = true): MyGraph[VD, ED] = {
    vertices.unpersist (blocking)
    this
  }


  override def unpersistVertices(blocking: Boolean = true): MyGraph[VD, ED] = {
    vertices.unpersist (blocking)
    // TODO: unpersist the replicated vertices in `replicatedVertexView` but leave the edges alone
    this
  }

  override def degreesRDD(edgeDirection: EdgeDirection): MyLocalVertexMessage[Int] = {
    if (edgeDirection == EdgeDirection.In) {
      aggregateMessages (_.sendToDst (1), _ + _, TripletFields.None)
    } else if (edgeDirection == EdgeDirection.Out) {
      aggregateMessages (_.sendToSrc (1), _ + _, TripletFields.None)
    } else { // EdgeDirection.Either
      aggregateMessages (ctx => {
        ctx.sendToSrc (1); ctx.sendToDst (1)
      }, _ + _,
        TripletFields.None)
    }
  }

  override def mapTriplets[ED2: ClassTag](
      map: MyEdgeTriplet[VD, ED] => ED2,
      tripletFields: TripletFields): MyGraph[VD, ED2] = {

    val newVertices = vertices.mapEdgePartitions{(part) =>
      part.mapTriplets (map)
    }
    new MyGraphImpl (newVertices)
  }

  override def mapVertices[VD2: ClassTag]
  (f: (VertexId, VD) => VD2)(implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED] = {
    // The implicit parameter eq will be populated by the compiler if VD and VD2 are equal, and left
    // null if not
    // if (eq != null) {
      // vertices.cache ()
      // The map preserves type, so we can use incremental replication
      val newVerts = vertices.mapVertexPartitions (_.map(f))
      new MyGraphImpl (newVerts)
    // } else {
      // The map does not preserve type, so we must re-replicate all vertices
    //   new MyGraphImpl (vertices.mapVertexPartitions (_.map(f)))
    // }
  }

  override def joinLocalVertices[U: ClassTag](table: RDD[(Int, U)], needActive: Boolean)
    (mapFunc: (VertexId, VD, U) => VD): MyGraph[VD, ED] = {
    val uf = (id: VertexId, data: VD, o: Option[U]) => {
      o match {
        case Some (u) => mapFunc (id, data, u)
        case None => data
      }
    }
    outerJoinLocalVertices (table, needActive)(uf)
  }

  override def outerJoinLocalVertices[U: ClassTag, VD2: ClassTag]
  (other: RDD[(Int, U)], needActive: Boolean)
    (updateF: (VertexId, VD, Option[U]) => VD2)
    (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED] = {
    // The implicit parameter eq will be populated by the compiler if VD and VD2 are equal, and left
    // null if not

    // vertices.cache ()

    // updateF preserves type, so we can use incremental replication
    val newVerts = vertices.localLeftJoin (other, needActive)(updateF)
    new MyGraphImpl (newVerts)
  }

  override def joinVertices[U: ClassTag](table: RDD[(VertexId, U)])
    (mapFunc: (VertexId, VD, U) => VD): MyGraph[VD, ED] = {
    val uf = (id: VertexId, data: VD, o: Option[U]) => {
      o match {
        case Some (u) => mapFunc (id, data, u)
        case None => data
      }
    }
    outerJoinVertices (table)(uf)
  }



  override def outerJoinVertices[U: ClassTag, VD2: ClassTag]
  (other: RDD[(VertexId, U)])
    (updateF: (VertexId, VD, Option[U]) => VD2)
    (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED] = {
    // The implicit parameter eq will be populated by the compiler if VD and VD2 are equal, and left
    // null if not

    vertices.cache ()

    // updateF preserves type, so we can use incremental replication
    val newVerts = vertices.leftJoin (other)(updateF)
    new MyGraphImpl (newVerts)
  }

  override def mapReduceTriplets[A: ClassTag](
      mapFunc: MyEdgeTriplet[VD, ED] => Iterator[(VertexId, A)],
      reduceFunc: (A, A) => A,
      activeSetOpt: EdgeDirection = EdgeDirection.Either): MyLocalVertexMessage[A] = {
    def sendMsg(ctx: MyVertexContext[VD, ED, A]) {
      mapFunc (ctx.toEdgeTriplet).foreach { kv =>
        val id = kv._1
        val msg = kv._2
        if (id == ctx.srcId) {
          ctx.sendToSrc (msg)
        } else {
          assert (id == ctx.dstId)
          ctx.sendToDst (msg)
        }
      }
    }

    aggregateMessagesWithActiveSet (
      sendMsg, reduceFunc, TripletFields.All, activeSetOpt)
  }

  override def aggregateMessagesWithActiveSet[A: ClassTag](
      sendMsg: MyVertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields,
      activeSetOpt: EdgeDirection): MyLocalVertexMessage[A] = {
    val startTime = System.currentTimeMillis
    // cache the vertices here, it is used for msg generation and index update
    vertices.cache ()
    // For each vertex, replicate its attribute only to partitions where it is
    // in the relevant position in an edge.
    val activeDirectionOpt = activeSetOpt
    //debug

    // Map and combine.
    val preAgg = vertices.partitionsRDD.mapPartitions (_.flatMap (
      vertexPartition =>
        activeDirectionOpt match {
          case EdgeDirection.Both =>
            vertexPartition
              .aggregateMessagesEdgeScan (sendMsg, mergeMsg, tripletFields, EdgeActiveness.Both)
          case EdgeDirection.Either =>
            vertexPartition
              .aggregateMessagesEdgeScan (sendMsg, mergeMsg, tripletFields, EdgeActiveness.Either)
          case EdgeDirection.Out =>
            vertexPartition
              .aggregateMessagesEdgeScan (sendMsg, mergeMsg, tripletFields, EdgeActiveness.SrcOnly)
          case EdgeDirection.In =>
            vertexPartition
              .aggregateMessagesEdgeScan (sendMsg, mergeMsg, tripletFields, EdgeActiveness.DstOnly)
          case _ => // None
            println("none")
            vertexPartition
              .aggregateMessagesEdgeScan (sendMsg, mergeMsg, tripletFields, EdgeActiveness.Neither)
        }
    )
    ).setName ("GraphImpl.aggregateMessages - preAgg")


    // val preNum =preAgg.count()
    // preAgg.foreach(_.RemoteMsgs.foreach(println))
    // println("Number   "+ preNum +"  It took %d ms count preAgg".format(System.currentTimeMillis - startTime))
    // val mid = System.currentTimeMillis

    // preAgg.cache()
    /*
    val partitioner = new HashPartitioner(this.vertices.numPartitions)
    val diffMsgs = preAgg.mapPartitionsWithIndex { (pid, msgs) =>
      // println("in graph: " + vertices.getNumPartitions)
      val lrMsgs = msgs.map { msg =>
        if (partitioner.getPartition(msg._1) == pid) {
          (msg._1, (msg._2, true))
        } else {
          (msg._1, (msg._2, false))
        }
      }

      lrMsgs
    }
    */

    // val r = vertices.aggregateUsingIndex (preAgg, mergeMsg)
    //    val rNum = r.count()
    //

    // preAgg.foreach(msgs => msgs.localMsgs.foreach(println))


    val r = vertices.aggregateLocalUsingIndex(preAgg, mergeMsg)
    // r.count()
    // println("  It took %d ms count aggregate".format(System.currentTimeMillis - mid))

    r
  }

  override def toGraphX: Graph[VD, ED] = {
    val edges = vertices.toEdgeRDD
    GraphImpl.fromEdgeRDD(edges, null.asInstanceOf[VD],
      StorageLevel.MEMORY_ONLY, StorageLevel.MEMORY_ONLY)
  }
}

object MyGraphImpl {

  def fromExistingRDDs[VD: ClassTag, ED: ClassTag](
      vertices: MyVertexRDD[VD, ED]
  ): MyGraphImpl[VD, ED] = {
    val graph = new MyGraphImpl (vertices)
    println("graph partiton: " + graph.vertices.getNumPartitions)
    graph
  }

  def fromEdgeList[VD: ClassTag]
  (
      // edgeList: RDD[(VertexId, VertexId)],
      edgeList: RDD[(VertexId, Iterable[VertexId])],
      defaultVertexAttr: VD,
      edgeStorageLevel: StorageLevel,
      vertexStorageLevel: StorageLevel): MyGraphImpl[VD, Int] = {
    val vertices: MyVertexRDD[VD, Int] =
      MyVertexRDD.fromEdges [VD, Int](edgeList)
        .withTargetStorageLevel (vertexStorageLevel)
    fromExistingRDDs (vertices)
  }
}
