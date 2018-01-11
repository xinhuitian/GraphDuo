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

import org.apache.spark.{HashPartitioner, OneToOneDependency}

import org.apache.spark.graphx.EdgeRDD
import org.apache.spark.graphx.impl.{EdgePartition, EdgeRDDImpl}
import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.collection.PrimitiveVector

/**
 * Created by sunny on 4/26/16.
 */
class MyVertexRDDImpl[VD: ClassTag, ED: ClassTag] private[graphv]
(@transient val partitionsRDD: RDD[MyVertexPartition[VD, ED]], val numPartitions: Int,
    val targetStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY)
  extends MyVertexRDD[VD, ED](partitionsRDD.context,
    List (new OneToOneDependency (partitionsRDD))) {

  override def getActiveNums: Long = partitionsRDD.mapPartitions { iter =>
    val part = iter.next()
    Iterator(part.activeSet.iterator.length)
  }.reduce(_ + _)

  override val partitioner =
    partitionsRDD.partitioner.orElse(Some(new HashPartitioner(numPartitions)))

  override def aggregateUsingIndex[VD2: ClassTag](
      messages: RDD[(VertexId, VD2)], reduceFunc: (VD2, VD2) => VD2): MyVertexMessage[VD2] = {
   //  val localMsgs = messages.filter(_._2._2 == true).map(msg => (msg._1, msg._2._1))
   //  val remoteMsgs = messages.filter(_._2._2 == false).map(msg => (msg._1, msg._2._1))
    val shuffled = messages.partitionBy (new HashPartitioner (partitionsRDD.getNumPartitions)) // 60ms remove cache()
    val parts = partitionsRDD.zipPartitions (shuffled, true) {
      (thisIter, msgIter) =>
      thisIter.map (_.aggregateUsingIndex (msgIter, reduceFunc))
    } //add cache make it fast  important!!

    /*
    val finalParts = parts.zipPartitions(localMsgs, true) { (thisIter, msgIter) =>
      thisIter.map (_.aggregateUsingIndex (msgIter, reduceFunc))
    }
    */
    this.withPartitionsRDD [VD2](parts)
  }

  override def aggregateLocalUsingIndex[VD2: ClassTag](
      messages: RDD[(VertexId, VD2)], reduceFunc: (VD2, VD2) => VD2): MyLocalVertexMessage[VD2] = {
    val startTime = System.currentTimeMillis()
    // messages.cache()
    // val localMsgs = messages.flatMap(_.localMsgs)
    println("localMsgs")
    // localMsgs.foreach(println)
    val shuffled = messages.partitionBy(new HashPartitioner (partitionsRDD.getNumPartitions)) //60ms  remove cache()
    // shuffled.count()
    // println("Shuffle Time: " + (System.currentTimeMillis() - startTime))
    val midTime = System.currentTimeMillis()
    val parts = partitionsRDD.zipPartitions (shuffled, true) {
      (thisIter, shuffleMsgIter) =>
      thisIter.map (_.aggregateLocalUsingIndex(shuffleMsgIter, reduceFunc))
    }.cache() //add cache make it fast  important!!
    // parts.count()
    // println("local aggre time: " + (System.currentTimeMillis() - midTime))

    /*
    val finalParts = parts.zipPartitions(localMsgs, true) { (thisIter, msgIter) =>
      thisIter.map (_.aggregateUsingIndex (msgIter, reduceFunc))
    }
    */
    this.withPartitionsRDD [VD2](parts)
  }

  override def withPartitionsRDD[VD2: ClassTag](
      partitionsRDD: RDD[MyVertexPartition[VD2, ED]]): MyVertexRDD[VD2, ED] = {
    new MyVertexRDDImpl (partitionsRDD, numPartitions, this.targetStorageLevel)
  }

  override def withPartitionsRDD[VD2: ClassTag](
      partitionsRDD: RDD[MyShippableVertexPartition[VD2]]):
  MyVertexMessage[VD2] = {
    new MyVertexMessageImpl (partitionsRDD, this.targetStorageLevel)
  }

  override def withPartitionsRDD[VD2: ClassTag](
      partitionsRDD: RDD[MyShippableLocalVertexPartition[VD2]]):
  MyLocalVertexMessage[VD2] = {
    new MyLocalVertexMessageImpl (partitionsRDD, this.targetStorageLevel)
  }

  override private[graphv] def withTargetStorageLevel(
      targetStorageLevel: StorageLevel): MyVertexRDD[VD, ED] = {
    new MyVertexRDDImpl[VD, ED](this.partitionsRDD, numPartitions, targetStorageLevel)
  }



  override def leftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: RDD[(VertexId, VD2)])
    (f: (VertexId, VD, Option[VD2]) => VD3)
  : MyVertexRDD[VD3, ED] = {
    // Test if the other vertex is a VertexRDD to choose the optimal join strategy.
    // If the other set is a VertexRDD then we use the much more efficient leftZipJoin
    other match {
      case other: MyVertexMessage[_] if this.partitioner == other.partitioner =>
        println("left join same partitioner")
        leftZipJoin (other)(f)
      case _ =>
        this.withPartitionsRDD (
          partitionsRDD.zipPartitions (
            other.partitionBy (this.partitioner.get), preservesPartitioning = true){
            (partIter, msgs) => partIter.flatMap (v => Iterator (v.leftJoin(msgs)(f)))
          }
        )
    }
  }

  // txh added
  def localLeftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: RDD[(Int, VD2)], needActive: Boolean = false)(f: (VertexId, VD, Option[VD2]) => VD3)
  : MyVertexRDD[VD3, ED] = {
    other match {
      case other: MyLocalVertexMessage[_] if this.partitioner == other.partitioner =>
        println("left join same partitioner")
        localLeftZipJoin (other, needActive)(f)
      case _ =>
        this.withPartitionsRDD (
          partitionsRDD.zipPartitions (
            other.partitionBy (this.partitioner.get), preservesPartitioning = true){
            (partIter, msgs) => partIter.flatMap (v => Iterator (v.localLeftJoin (msgs)(f)))
          }
        )
    }
  }

  override def foreachEdge(f: (VertexId, ED) => Unit): Unit = {
    partitionsRDD.foreach (v => {
      v.foreachEdgePartition (f)
    })
  }

  def mapEdgePartitions[VD2: ClassTag, ED2: ClassTag](
      f: (MyVertexPartition[VD, ED]) => MyVertexPartition[VD2, ED2]): MyVertexRDDImpl[VD2, ED2] = {
    this.withPartitionsRDD [VD2, ED2](partitionsRDD.mapPartitions ({iter =>
      if (iter.hasNext) {
        val ep = iter.next ()
        Iterator (f (ep))
      } else {
        Iterator.empty
      }
    }))
  }

  private[graphv] def withPartitionsRDD[VD2: ClassTag, ED2: ClassTag](
      partitionsRDD: RDD[MyVertexPartition[VD2, ED2]]): MyVertexRDDImpl[VD2, ED2] = {
    new MyVertexRDDImpl (partitionsRDD, numPartitions, this.targetStorageLevel)
  }

  override def leftZipJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyVertexMessage[VD2])(f: (VertexId, VD, Option[VD2]) => VD3): MyVertexRDD[VD3, ED] = {
    val newPartitionsRDD = partitionsRDD.zipPartitions (
      other.partitionsRDD, preservesPartitioning = true
    ){(thisIter, otherIter) =>
      val thisPart = thisIter.next ()
      val otherPart = otherIter.next ()
      Iterator (thisPart.leftJoin (otherPart)(f))
    }
    this.withPartitionsRDD (newPartitionsRDD)
  }

  // preservesPartitioning: get the zipPartition cached?
  override def localLeftZipJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyLocalVertexMessage[VD2], needActive: Boolean = false)
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexRDD[VD3, ED] = {
    val newPartitionsRDD = partitionsRDD.zipPartitions (
      other.partitionsRDD
    ){(thisIter, otherIter) =>
      val thisPart = thisIter.next ()
      val otherPart = otherIter.next ()
      Iterator (thisPart.localLeftJoin(otherPart, needActive)(f))
    }
    this.withPartitionsRDD (newPartitionsRDD)
  }

  override def mapVertexPartitions[VD2: ClassTag](
      f: MyVertexPartition[VD, ED] => MyVertexPartition[VD2, ED])
  : MyVertexRDD[VD2, ED] = {
    val newPartitionsRDD = partitionsRDD.mapPartitions (_.map(f),
      preservesPartitioning = true)
    this.withPartitionsRDD (newPartitionsRDD)
  }

  override def cache(): this.type = {
    partitionsRDD.persist(targetStorageLevel)
    this
  }

  override def persist(newLevel: StorageLevel): this.type = {
    partitionsRDD.persist(newLevel)
    this
  }

  override def unpersist(blocking: Boolean = true): this.type = {
    partitionsRDD.unpersist(blocking)
    this
  }

  override def toEdgeRDD: EdgeRDDImpl[ED, VD] = {
    val edges: RDD[(Int, EdgePartition[ED, VD])] = partitionsRDD
      .mapPartitionsWithIndex{ (pid, partIter) =>
      val vertPart = partIter.next()
      val srcIndex = vertPart.vertexIds
      val localDstIds = vertPart.localDstIds
      val localSrcs = new PrimitiveVector[Int]

      val vertIndex = new GraphXPrimitiveKeyOpenHashMap[VertexId, Int]

      val local2global = vertPart.local2global
      val global2local = vertPart.global2local

      var pos = 0
      while (pos < srcIndex.length) {
        val srcId = pos
        val (dstIndex, dstPos) = srcIndex(pos)
        // println(s"get dstIndex and dstPos: $dstIndex, $dstPos")

        var i = 0
        while (i < dstPos) {
          // val dstId = dstIds (dstIndex + i)
          // println("dstId: " + local2global(pos))
          localSrcs += pos

          i += 1
        }

        vertIndex.update(local2global(pos), dstIndex)
        pos += 1
      }
        val localSrcIds = localSrcs.trim.toArray

/*
      srcIndex.foreach { ids =>
        vertIndex.update(local2global(ids._1), ids._2)
      }
      */

      val activeSet = vertIndex.keySet

      val edgePartition = new EdgePartition(localSrcIds,
        localDstIds, vertPart.edgeAttrs, vertIndex, global2local,
        local2global, new Array[VD](local2global.length), Some(activeSet))

      Iterator((pid, edgePartition))
    }

    new EdgeRDDImpl(edges)
  }
}
