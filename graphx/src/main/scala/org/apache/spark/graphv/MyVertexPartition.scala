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

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

import org.apache.spark.graphx.TripletFields
import org.apache.spark.graphx.impl.EdgeActiveness
import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.util.collection.{BitSet, PrimitiveVector}

/**
 * Created by sunny on 4/25/16.
 */

// txh: get local msgs
case class AllMsgs[A: ClassTag](localMsgs: Iterator[(Int, A)],
    RemoteMsgs: Iterator[(VertexId, A)])

class MyVertexPartition[
@specialized (Char, Int, Boolean, Byte, Long, Float, Double) VD: ClassTag, ED: ClassTag](
    // val dstIds: Array[VertexId],
    val localDstIds: Array[Int],
    val attrs: Array[VD], // src attr, the index is based on the local2global index
    val vertexIds: Array[(Int, Int)], // srcId to (dstIndex, length)
    val edgeAttrs: Array[ED],
    val global2local: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    val local2global: Array[VertexId],
    val activeSet: BitSet)
  extends Serializable {
  // def numActives: Option[Int] = activeSet.map(_.size)
  def vertexSize: Int = vertexIds.size

  def edgeSize: Int = localDstIds.size

  val vertexAttrSize: Int = attrs.size

  val totalVertexSize: Int = local2global.size

  def iterator: Iterator[(VertexId, VD)] = new Iterator[(VertexId, VD)] {
    private[this] var pos = 0

    private[this] val id = local2global

    override def hasNext: Boolean = (pos < vertexAttrSize)

    override def next(): (VertexId, VD) = {
      val vid = id (pos)
      val attr = attrs (pos)
      pos += 1
      (vid, attr)
    }
  }

  def foreachEdgePartition(f: (VertexId, ED) => Unit): Unit = {
    val iter = vertexIds.zipWithIndex.toIterator
    while (iter.hasNext) {
      val tuple = iter.next ()
      val srcId = local2global(tuple._2)
      val (dstIndex, dstPos) = tuple._1
      var i = 0
      //      println("start : " +srcId + "  "+ dstIndex +" " +dstPos)
      while (i < dstPos) {
        //          val dstId = dstIds(dstIndex + i)
        //            val srcAttr = attrs(global2local(srcId))
        f (srcId, edgeAttrs (dstIndex + i))
        i += 1
      }
    }
  }

  def edges: Iterator[(VertexId, VertexId)] = {
    val edgeArray = new PrimitiveVector[(VertexId, VertexId)]
    var pos = activeSet.nextSetBit (0)
    while (pos >= 0) {
      val srcId = local2global (pos)
      val (dstIndex, dstPos) = vertexIds(pos)
      // println(s"get dstIndex and dstPos: $dstIndex, $dstPos")

      var i = 0
      while (i < dstPos) {
        // val dstId = dstIds (dstIndex + i)
        val localDstId = localDstIds(dstIndex + i)
        edgeArray += (srcId, local2global(localDstId))
        // println("dstId: " + local2global(localDstId))
        i += 1
      }
      pos = activeSet.nextSetBit (pos + 1)
    }
    edgeArray.trim().iterator
  }

  def map[VD2: ClassTag](f: (VertexId, VD) => VD2): MyVertexPartition[VD2, ED] = {
    // Construct a view of the map transformation
    val newValues = new Array[VD2](vertexAttrSize)
    var i = 0
    while (i < vertexAttrSize) {
      newValues (i) = f (local2global (i), attrs (i))
      i += 1
    }
    new MyVertexPartition[VD2, ED](localDstIds, newValues,
      vertexIds, edgeAttrs, global2local, local2global, activeSet)
  }

  def mapTriplets[ED2: ClassTag](f: MyEdgeTriplet[VD, ED] => ED2)
  : MyVertexPartition[VD, ED2] = {
    val newData = new Array[ED2](edgeAttrs.length)
    val iter = vertexIds.zipWithIndex.iterator
    while (iter.hasNext) {
      val tuple = iter.next ()
      val srcId = tuple._2
      val (dstIndex, dstPos) = tuple._1
      var i = 0
      while (i < dstPos) {
        val triplet = new MyEdgeTriplet[VD, ED]
        triplet.dstId = local2global(localDstIds (dstIndex + i))
        triplet.srcAttr = attrs (srcId)
        triplet.attr = edgeAttrs (dstIndex + i)
        newData (dstIndex + i) = f (triplet)
        i += 1
      }
    }
    new MyVertexPartition (
      localDstIds, attrs, vertexIds, newData, global2local, local2global, activeSet)
  }

  def leftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: Iterator[(VertexId, VD2)])
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexPartition[VD3, ED] = {
    leftJoin (createUsingIndex (other))(f)
  }

  def leftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyShippableVertexPartition[VD2])
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexPartition[VD3, ED] = {
    if (global2local != other.global2local) {
      println("Not the same index for leftJoin")
      // logWarning ("Joining two VertexPartitions with different indexes is slow.")
      leftJoin (createUsingIndex (other.iterator))(f)
    } else {
      val newValues = new Array[VD3](vertexAttrSize)

      //var i = other.mask.nextSetBit(0)

      var i = 0

      while (i < vertexAttrSize) {
        val otherV: Option[VD2] = if (other.mask.get (i)) Some (other.values (i)) else None
        newValues (i) = f (local2global (i), attrs (i), otherV)
        if (attrs (i) == newValues (i)) {
          activeSet.unset (i)
        } else {
          activeSet.set (i)
        }
        i += 1
      }

      new MyVertexPartition[VD3, ED](localDstIds, newValues,
        vertexIds, edgeAttrs, global2local, local2global, activeSet)
    }
  }

  def localLeftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: Iterator[(Int, VD2)], needActive: Boolean = false)
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexPartition[VD3, ED] = {
    localLeftJoin(createLocalUsingIndex(other), needActive)(f)
  }

  def localLeftJoin[VD2: ClassTag, VD3: ClassTag]
  (other: MyShippableLocalVertexPartition[VD2], needActive: Boolean)
    (f: (VertexId, VD, Option[VD2]) => VD3): MyVertexPartition[VD3, ED] = {
    val newValues = new Array[VD3](vertexAttrSize)
    // val newActiveSet = new BitSet(vertexAttrSize)
    // newActiveSet.clearUntil(vertexAttrSize)

    // println("local left join")
    // other.values.foreach(v => print(v + " "))
    // println

    //var i = other.mask.nextSetBit(0)

    var i = 0

    // other.mask.iterator.foreach(v => println(local2global(v)))

    while (i < vertexAttrSize) {
      val otherV: Option[VD2] = if (other.mask.get (i)) Some (other.values (i)) else None
      if (otherV == None) {
        // println(local2global(i) + " get a None msg")
      }
      newValues (i) = f (local2global (i), attrs (i), otherV)
      // println("new Value: " + local2global(i), newValues(i))

      if (needActive == true) {
        if (attrs (i) != newValues (i)) {
          activeSet.set(i)
        } else {
          activeSet.unset(i)
        }
      }
      i += 1
    }

    new MyVertexPartition[VD3, ED](localDstIds, newValues,
      vertexIds, edgeAttrs, global2local, local2global, activeSet)

  }

  def createLocalUsingIndex[VD2: ClassTag](iter: Iterator[(Int, VD2)])
  : MyShippableLocalVertexPartition[VD2] = {
    val newMask = new BitSet (vertexAttrSize)
    val newValues = new Array[VD2](vertexAttrSize)
    iter.foreach{pair =>
      val pos = pair._1
      newMask.set (pos)
      newValues (pos) = pair._2
    }
    new MyShippableLocalVertexPartition[VD2](global2local, newValues, newMask)
  }


  def createUsingIndex[VD2: ClassTag](iter: Iterator[(VertexId, VD2)])
  : MyShippableVertexPartition[VD2] = {
    val newMask = new BitSet (vertexAttrSize)
    val newValues = new Array[VD2](vertexAttrSize)
    iter.foreach{pair =>
      val pos = global2local.getOrElse (pair._1, -1)
      if (pos >= 0) {
        newMask.set (pos)
        newValues (pos) = pair._2
      }
    }
    new MyShippableVertexPartition[VD2](global2local, local2global, newValues, newMask)
  }


  // txh: change shippablevertexPartition creation
  // after aggregation, all the msgs are represented as the int type
  def aggregateUsingIndex[VD2: ClassTag](
      iter: Iterator[(VertexId, VD2)],
      reduceFunc: (VD2, VD2) => VD2): MyShippableVertexPartition[VD2] = {
    val ship = new MyShippableVertexPartition (global2local, local2global,
      new Array[VD2](vertexAttrSize), new BitSet (vertexAttrSize))
    ship.aggregateUsingIndex (iter, reduceFunc)
  }

  // txh added
  def aggregateLocalUsingIndex[VD2: ClassTag](
      iter: Iterator[(VertexId, VD2)],
      reduceFunc: (VD2, VD2) => VD2): MyShippableLocalVertexPartition[VD2] = {
    val ship = new MyShippableLocalVertexPartition (global2local,
      new Array[VD2](vertexAttrSize), new BitSet (vertexAttrSize))
    ship.aggregateUsingIndex (iter, reduceFunc)
  }

  def aggregateMessagesEdgeScan[A: ClassTag](
      sendMsg: MyVertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields,
      activeness: EdgeActiveness): Iterator[(VertexId, A)] = {
    // println("global2local size: " + global2local.size +"  attrs size: " + attrs.length +"  iter size:  "+
    //  vertexIds.size)
    // txh: aggregates on all vertices, can contain masters
    val aggregates = new Array[A](local2global.size)
    // txh: bitset for all vertices
    val bitset = new BitSet (local2global.size)

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset)

    var pos = activeSet.nextSetBit (0)
    while (pos >= 0) {
      val srcId = local2global (pos)
      val (dstIndex, dstPos) = vertexIds(pos)
      // println(s"get dstIndex and dstPos: $dstIndex, $dstPos")

      var i = 0
      while (i < dstPos) {
        // val dstId = dstIds (dstIndex + i)
        val localDstId = localDstIds(dstIndex + i)
        // println("dstId: " + local2global(localDstId))
        // txh: do not need to consider the edge activeness

        val srcAttr = if (tripletFields.useSrc) attrs (pos) else null.asInstanceOf [VD]
        ctx.set (srcId, local2global(localDstId), pos, localDstId, srcAttr, edgeAttrs (dstIndex + i))
        sendMsg (ctx)
        // }
        i += 1
      }
      pos = activeSet.nextSetBit (pos + 1)
    }

    bitset.iterator
      .map(localId => (local2global (localId), aggregates (localId)))

    // println("Generated Msgs: " + localMsgs.length)
    // localMsgs.foreach(msg => println(msg + " "))
    // println
    // localMsgs.foreach{ msg => print(msg + "") }
    // println

    // Iterator(AllMsgs(localMsgs, remoteMsgs))
    // bitset.iterator.map{localId => (local2global (localId), aggregates (localId))}
  }
}

private class AggregatingVertexContext[VD, ED, A](
    mergeMsg: (A, A) => A,
    aggregates: Array[A],
    bitset: BitSet)
  extends MyVertexContext[VD, ED, A] {

  private[this] var _srcId: VertexId = _
  private[this] var _dstId: VertexId = _
  private[this] var _localSrcId: Int = _
  private[this] var _localDstId: Int = _
  private[this] var _srcAttr: VD = _
  private[this] var _attr: ED = _

  def set(
      srcId: VertexId, dstId: VertexId,
      localSrcId: Int, localDstId: Int,
      srcAttr: VD, attr: ED) {
    _srcId = srcId
    _dstId = dstId
    _localSrcId = localSrcId
    _localDstId = localDstId
    _srcAttr = srcAttr
    _attr = attr
  }

  def setSrcOnly(srcId: VertexId, localSrcId: Int, srcAttr: VD) {
    _srcId = srcId
    _localSrcId = localSrcId
    _srcAttr = srcAttr
  }

  def setRest(dstId: VertexId, localDstId: Int, dstAttr: VD, attr: ED) {
    _dstId = dstId
    _localDstId = localDstId
    _attr = attr
  }

  override def srcId: VertexId = _srcId

  override def dstId: VertexId = _dstId

  override def srcAttr: VD = _srcAttr

  override def attr: ED = _attr

  override def sendToDst(msg: A) {
    send (_localDstId, msg)
  }

  override def sendToSrc(msg: A) {
    send (_localSrcId, msg)
  }

  @inline private def send(localId: Int, msg: A) {
    if (bitset.get (localId)) {
      aggregates (localId) = mergeMsg (aggregates (localId), msg)
    } else {
      aggregates (localId) = msg
      bitset.set (localId)
    }
  }
}