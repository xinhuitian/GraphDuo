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

import org.apache.spark._

import org.apache.spark.graphx.EdgeRDD
import org.apache.spark.graphx.impl.EdgeRDDImpl
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by sunny on 4/25/16.
 */
abstract class MyVertexRDD[VD, ED](
    sc: SparkContext,
    deps: Seq[Dependency[_]]) extends RDD[(VertexId, VD)](sc, deps) {

  // implicit protected def vdTag: ClassTag[VD]

  private[graphv] def getActiveNums: Long

  private[graphv] def partitionsRDD: RDD[MyVertexPartition[VD, ED]]

  private[graphv] def numPartitions: Int

  override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  override def compute(part: Partition, context: TaskContext): Iterator[(VertexId, VD)] = {
    firstParent [MyVertexPartition[VD, _]].iterator (part, context).next().iterator
    /*
    val p = firstParent [MyVertexPartition[VD, _]].iterator (part, context).next().iterator
    if (p.hasNext) {
      p.next ().iterator.map (_.copy ())
    } else {
      Iterator.empty
    }
    */
  }

  def apply[VD: ClassTag](vertices: RDD[(VertexId, VD)]): RDD[(VertexId, VD)] = {
    val vPartitioned: RDD[(VertexId, VD)] = vertices.partitioner match {
      case Some (p) => vertices
      case None => vertices.partitionBy (new HashPartitioner (vertices.partitions.length))
    }
    vPartitioned
  }

  private[graphv] def mapEdgePartitions[VD2: ClassTag, ED2: ClassTag](
      f: (MyVertexPartition[VD, ED]) => MyVertexPartition[VD2, ED2]): MyVertexRDDImpl[VD2, ED2]

  def leftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: RDD[(VertexId, VD2)])
    (f: (VertexId, VD, Option[VD2]) => VD3)
  : MyVertexRDD[VD3, ED]

  // txh added
  def localLeftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: RDD[(Int, VD2)], needActive: Boolean = false)(f: (VertexId, VD, Option[VD2]) => VD3)
  : MyVertexRDD[VD3, ED]

  private[graphv] def withTargetStorageLevel(
      targetStorageLevel: StorageLevel): MyVertexRDD[VD, ED]

  def leftZipJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyVertexMessage[VD2])(f: (VertexId, VD, Option[VD2]) => VD3): MyVertexRDD[VD3, ED]

  def localLeftZipJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyLocalVertexMessage[VD2], needActive: Boolean)
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexRDD[VD3, ED]

  def foreachEdge(f: (VertexId, ED) => Unit): Unit

  private[graphv] def mapVertexPartitions[VD2: ClassTag](
      f: MyVertexPartition[VD, ED] => MyVertexPartition[VD2, ED])
  : MyVertexRDD[VD2, ED]


  def withPartitionsRDD[VD2: ClassTag](partitionsRDD: RDD[MyVertexPartition[VD2, ED]])
  : MyVertexRDD[VD2, ED]

  def withPartitionsRDD[VD2: ClassTag](partitionsRDD: RDD[MyShippableVertexPartition[VD2]])
  : MyVertexMessage[VD2]

  def withPartitionsRDD[VD2: ClassTag](partitionsRDD: RDD[MyShippableLocalVertexPartition[VD2]]):
  MyLocalVertexMessage[VD2]

  def aggregateUsingIndex[VD2: ClassTag](
      messages: RDD[(VertexId, VD2)], reduceFunc: (VD2, VD2) => VD2): MyVertexMessage[VD2]

  def aggregateLocalUsingIndex[VD2: ClassTag](
      messages: RDD[(VertexId, VD2)], reduceFunc: (VD2, VD2) => VD2): MyLocalVertexMessage[VD2]

  def toEdgeRDD: EdgeRDDImpl[ED, VD]
}

object MyVertexRDD {
  // def fromEdges[VD: ClassTag, ED: ClassTag](edges: RDD[(VertexId, VertexId)]): MyVertexRDD[VD, Int] = {
  def fromEdges[VD: ClassTag, ED: ClassTag](edges: RDD[(VertexId, Iterable[VertexId])])
  : MyVertexRDD[VD, Int] = {


    //    val vertexPartitions = edges.partitionsRDD.mapPartitions(_.flatMap(Function.tupled(
    //      (pid: PartitionID, edgePartition: MyEdgePartition[ED]) => {
    //        //highlight
    //        val map = new GraphXPrimitiveKeyOpenHashMap[VertexId,Edge[ED]]
    //        edgePartition.iterator.foreach(e => map.changeValue(e.srcId, e, (b: Edge[ED]) => e))
    //        map
    //      })))
    //
    //    //if need repartition
    //    val partition2impl = vertexPartitions.mapPartitionsWithIndex((vid, vp) => {
    //      val builder = new MyVertexPartitionBuilder[VD, ED]
    //      vp.foreach(i => builder.add(i._2))
    //      Iterator((vid, builder.toVertexPartition))
    //    })
    //

    //    edges.foreach(v =>{
    //      println(v._1)
    //      v._2.foreach(a =>print(a +" "))
    //      println()
    //    })

    println("edge partition: " + edges.getNumPartitions)
    val vertexPartitions = edges.mapPartitions (list => {
      val builder = new MyVertexPartitionBuilder[VD]()
      list.foreach (iter =>
        builder.add (iter)
      )
      Iterator (builder.toVertexPartition)
    }).cache ()
    println("vertex partition: " + vertexPartitions.getNumPartitions)

    new MyVertexRDDImpl (vertexPartitions, vertexPartitions.getNumPartitions)
  }
}
