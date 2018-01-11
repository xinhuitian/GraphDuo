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

import scala.language.implicitConversions
import scala.reflect.ClassTag

import org.apache.spark.graphx.{Graph, TripletFields}
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel


/**
 * The Graph abstractly represents a graph with arbitrary objects
 * associated with vertices and edges.  The graph provides basic
 * operations to access and manipulate the data associated with
 * vertices and edges as well as the underlying structure.  Like Spark
 * RDDs, the graph is a functional data-structure in which mutating
 * operations return new graphs.
 *
 * @tparam VD the vertex attribute type
 * @tparam ED the edge attribute type
 */
abstract class MyGraph[VD: ClassTag, ED: ClassTag] protected() extends Serializable {

  val vertices: MyVertexRDD[VD, ED]

  def getActiveNums: Long

  def persist(newLevel: StorageLevel = StorageLevel.MEMORY_ONLY): MyGraph[VD, ED]

  def cache(): MyGraph[VD, ED]


  def unpersist(blocking: Boolean = true): MyGraph[VD, ED]

  def mapVertices[VD2: ClassTag](map: (VertexId, VD) => VD2)
    (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED]

  def unpersistVertices(blocking: Boolean = true): MyGraph[VD, ED]

  //  def joinVertices[U: ClassTag](table: RDD[(VertexId, U)])(mapFunc: (VertexId, VD, U) => VD)
  //  : MyGraph[VD, ED]

  def joinVertices[U: ClassTag](table: RDD[(VertexId, U)])(mapFunc: (VertexId, VD, U) => VD)
  : MyGraph[VD, ED]

  // txh adds, used specially for pregel.
  def joinLocalVertices[U: ClassTag](table: RDD[(Int, U)], needActive: Boolean)(
      mapFunc: (VertexId, VD, U) => VD)
  : MyGraph[VD, ED]

  def outerJoinLocalVertices[U: ClassTag, VD2: ClassTag]
  (other: RDD[(Int, U)], needActive: Boolean)
    (updateF: (VertexId, VD, Option[U]) => VD2)
    (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED]


  def mapReduceTriplets[A: ClassTag](
      mapFunc: MyEdgeTriplet[VD, ED] => Iterator[(VertexId, A)],
      reduceFunc: (A, A) => A,
      activeSetOpt: EdgeDirection = EdgeDirection.Either): MyLocalVertexMessage[A]

  @transient lazy val outDegrees: MyLocalVertexMessage[Int] =
    degreesRDD (EdgeDirection.Out).setName ("GraphOps.outDegrees")


  def degreesRDD(edgeDirection: EdgeDirection): MyLocalVertexMessage[Int]

  //  def mapTriplets[ED2: ClassTag](map: MyEdgeTriplet[VD, ED] => ED2): MyGraph[VD, ED2] = {
  //    mapTriplets((pid, iter) => iter.map(map), TripletFields.All)
  //  }
  def mapTriplets[ED2: ClassTag](
      map: MyEdgeTriplet[VD, ED] => ED2,
      tripletFields: TripletFields): MyGraph[VD, ED2]

  //= {
  //    mapTriplets((pid, iter) => map(iter), tripletFields)
  //  }

  //  def mapTriplets[ED2: ClassTag](
  //                                  map: (PartitionID, MyEdgeTriplet[VD, ED]) => ED2,
  //                                  tripletFields: TripletFields): MyGraph[VD, ED2]

  // txh: change the activedirection, no need to use the activeSet

  def aggregateMessages[A: ClassTag](
      sendMsg: MyVertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields = TripletFields.All,
      activeDirection: EdgeDirection = EdgeDirection.Either)
  : MyLocalVertexMessage[A] = {
    aggregateMessagesWithActiveSet (sendMsg, mergeMsg, tripletFields, activeDirection)
  }

  private[graphv] def aggregateMessagesWithActiveSet[A: ClassTag](
      sendMsg: MyVertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields,
      activeSetOpt: EdgeDirection)
  : MyLocalVertexMessage[A]


  def outerJoinVertices[U: ClassTag, VD2: ClassTag]
  (other: RDD[(VertexId, U)])
    (updateF: (VertexId, VD, Option[U]) => VD2)
    (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED]

  // added by txh in 7.30 2017
  def toGraphX: Graph[VD, ED]

  //  def outerJoinVertices[U: ClassTag, VD2: ClassTag]
  //  (other: RDD[(VertexId, U)])
  //  (updateF: (VertexId, VD, Option[U]) => VD2)
  //  (implicit eq: VD =:= VD2 = null): MyGraph[VD2, ED]
} // end of Graph


