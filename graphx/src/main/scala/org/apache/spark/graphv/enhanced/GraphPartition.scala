
package org.apache.spark.graphv.enhanced

import scala.reflect.ClassTag

import org.apache.spark.graphv.{Edge, EdgeDirection, VertexId}
import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.HashPartitioner

import org.apache.spark.util.collection.{BitSet, OpenHashSet, PrimitiveVector}

class MessageBlock[VD: ClassTag](val vids: Array[VertexId], val attrs: Array[VD])
  extends Serializable {
  def iterator: Iterator[(VertexId, VD)] =
    (0 until vids.length).iterator.map { i => (vids(i), attrs(i)) }
}

class LocalMessageBlock[VD: ClassTag](val vids: Array[Int], val attrs: Array[VD])
  extends Serializable {
  def iterator: Iterator[(Int, VD)] =
    (0 until vids.length).iterator.map { i => (vids(i), attrs(i)) }
}


class MixMessageBlock[@specialized (Char, Int, Boolean, Byte, Long, Float, Double)
VD: ClassTag, A: ClassTag](
    val mirrorLVids: Array[Int],
    val mirrorAttrs: Array[VD],
    val targetVids: Array[VertexId],
    val msgs: Array[A]) extends Serializable {

  def msgIterator: Iterator[(VertexId, A)] =
    (0 until targetVids.length).iterator.map { i => (targetVids(i), msgs(i)) }

  def syncIterator: Iterator[(Int, VD)] =
    (0 until mirrorAttrs.length).iterator.map { i => (mirrorLVids(i), mirrorAttrs(i)) }
}

/*
class MixMessageBlock[@specialized (Char, Int, Boolean, Byte, Long, Float, Double)
VD: ClassTag, A: ClassTag](
    val mirrorMsgs: Iterator[(Int, VD)],
    val pushMsgs: Iterator[(VertexId, A)]
) extends Serializable {

  def msgIterator: Iterator[(VertexId, A)] =
    pushMsgs

  def syncIterator: Iterator[(Int, VD)] =
    mirrorMsgs
}
*/


class LocalFinalMessages[VD: ClassTag](
    val values: Array[VD],
    val mask: BitSet) extends Serializable {
  def iterator: Iterator[(Int, VD)] =
    mask.iterator.map (ind => (ind, values (ind)))
}

class GlobalFinalMessages[VD: ClassTag](
    val l2g: Array[VertexId],
    val values: Array[VD],
    val mask: BitSet) extends Serializable {
  def iterator: Iterator[(VertexId, VD)] =
    mask.iterator.map (ind => (l2g(ind), values (ind)))
}

/*
 the vertices have three types:
 1. vertices with small degrees: have all the neighborIds locally
 2. vertices with large degrees: only have the pids of their neighbors
 3. mirrors of remote vertices with large degrees: neighbors in this partition
  */
class GraphPartition[
@specialized (Char, Int, Boolean, Byte, Long, Float, Double) VD: ClassTag, ED: ClassTag](
    // val dstIds: Array[VertexId], remove edges for mirror-based vertices
    val localDstIds: Array[Int],
    val masterAttrs: Array[VD], // src attr, the index is based on the local2global index
    val mirrorAttrs: Array[VD],
    val vertexIndex: Array[Int], // srcId to dstIndex
    // val mirrorIndex: GraphXPrimitiveKeyOpenHashMap[VertexId, (Int, Int)], // index of mirrors
    val edgeAttrs: Array[ED],
    val global2local: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    // layout: |SDVertices | mirrorsForRemoteLDMasters | mixMasters | LDMasters
    // | otherNeighbors
    val local2global: Array[VertexId],
    // structures for mirrors
    // val dstIdsForRemote: Array[Int],
    // val remoteEdgeAttrs: Array[ED],
    // remote vid to dstIndex
    // val remoteVidIndex: GraphXPrimitiveKeyOpenHashMap[VertexId, (Int, Int)],
    val indexStartPos: Int,
    val smallDegreeEndPos: Int,
    val largeDegreeMirrorEndPos: Int,
    val largeDegreeMasterEndPos: Int,
    val numPartitions: Int,
    val routingTable: RoutingTable,
    val activeSet: BitSet)
  extends Serializable {

  // val localVertexSize = smallDegreeSize + largeDegreeSize
  // must have at last one vertex
  require(local2global.length > 0)

  def vertexAttrSize: Int = largeDegreeMasterEndPos

  def masterSize: Int = vertexAttrSize

  def mirrorSize: Int = mirrorAttrs.length

  def srcMirrorSize: Int = largeDegreeMirrorEndPos - masterSize

  def edgeSize: Int = localDstIds.length

  def totalVertSize: Int = local2global.size

  val thisPid: Int = new HashPartitioner(numPartitions).getPartition(local2global(0))

  def iterator: Iterator[(VertexId, VD)] = local2global.zipWithIndex
    .filter(v => v._2 < masterSize)
    .map (v => (v._1, masterAttrs(v._2))).iterator

  def edgeIterator: Iterator[Edge[ED]] = {
    val edges = new Array[Edge[ED]](edgeAttrs.length)
    for (i <- 0 until vertexIndex.size - 1) {
      val localSrcId = i + indexStartPos
      val dstIndex = vertexIndex(i)
      val dstEndPos = vertexIndex(i + 1)
      for (j <- dstIndex until dstEndPos) {
        val edge = new Edge[ED]
        edge.dstId = local2global(localDstIds(j))
        edge.attr = edgeAttrs(j)
        edge.srcId = local2global(localSrcId)
        edges(j) = edge
      }
    }

    edges.iterator
  }

  def masterIterator: Iterator[(VertexId, VD)] = iterator

  // topology mirrors + propagation mirrors
  def mirrorIterator: Iterator[Int] = (masterSize until totalVertSize).iterator

  def remoteLDMirrorIterator
  : Iterator[Int] = (masterSize until largeDegreeMirrorEndPos).iterator

  // filter in mirrors and out mirrors from the localDstIds
  def remoteOutMirrorIterator: Iterator[Int] = {
    val vertexMap = new OpenHashSet[Int](totalVertSize - masterSize)
    localDstIds.foreach { v =>
      if (v >= masterSize) {
        vertexMap.add (v)
      }
    }
    vertexMap.iterator
  }

  def neighborIterator: Iterator[VertexId] = localDstIds.map(v => local2global(v)).iterator

  def localNeighborIterator: Iterator[Int] = localDstIds.iterator

  def isActive(vid: Int): Boolean = {
    activeSet.get(vid)
  }

  def activateAllMasters: GraphPartition[VD, ED] = {
    for (i <- 0 until masterSize) {
      activeSet.set(i)
    }

    this.withActiveSet(activeSet)
  }

  def getActiveNum: Long = activeSet.iterator.filter(_ < masterSize).length

  // used for computation init and vertices changing
  def mapVertices[VD2: ClassTag](
      f: (VertexId, VD) => VD2,
      needActive: Boolean = false): GraphPartition[VD2, ED] = {
    val newValues = new Array[VD2](masterSize)

    (0 until masterSize)
      .foreach { i =>
        newValues(i) = f (local2global(i), masterAttrs(i))
        if (needActive) {
          if (newValues(i) != masterAttrs(i)) {
            activeSet.set(i)
          } else {
            activeSet.unset(i)
          }
        }
      }

    new GraphPartition[VD2, ED](localDstIds, newValues,
      Array.fill[VD2](mirrorSize)(null.asInstanceOf[VD2]),
      vertexIndex, edgeAttrs, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, activeSet)
  }

  def mapMirrors(f: (VertexId, VD) => VD): GraphPartition[VD, ED] = {
    val newValues = new Array[VD](mirrorSize)
    // println("MirrorSize: " + mirrorSize)

    (0 until mirrorSize)
      .foreach { i =>
        newValues(i) = f (local2global(i + masterSize), mirrorAttrs(i))
        // println(newValues(i))
      }

    this.withMirrorValues(newValues)
  }

  def mapEdges[ED2: ClassTag](f: Edge[ED] => ED2): GraphPartition[VD, ED2] = {
    val newData = new Array[ED2](edgeAttrs.length)
    for (i <- 0 until vertexIndex.length - 1) {
      val localSrcId = i + indexStartPos
      val dstIndex = vertexIndex(i)
      val dstEndPos = vertexIndex(i + 1)
      for (j <- dstIndex until dstEndPos) {
        val edge = new Edge[ED]
        edge.attr = edgeAttrs(j)
        newData(j) = f (edge)
        // println(newData(j))
      }
    }

    new GraphPartition[VD, ED2](localDstIds, masterAttrs, mirrorAttrs,
      vertexIndex, newData, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, activeSet)
  }

  def mapTriplets[ED2: ClassTag](f: GraphVEdgeTriplet[VD, ED] => ED2)
  : GraphPartition[VD, ED2] = {
    val newData = new Array[ED2](edgeAttrs.length)
    for (i <- 0 until vertexIndex.length - 1) {
      val localSrcId = i + indexStartPos
      val dstIndex = vertexIndex(i)
      val dstEndPos = vertexIndex(i + 1)
      for (j <- dstIndex until dstEndPos) {
        val triplet = new GraphVEdgeTriplet[VD, ED]
        triplet.srcId = local2global(localSrcId)
        // println("Triplet: " + triplet.srcId + " " + attrs(localSrcId))
        triplet.dstId = local2global(localDstIds(j))
        triplet.srcAttr = if (localSrcId < masterSize) {
          // println(s"$localSrcId, $masterSize，${masterAttrs.length}")
          masterAttrs(localSrcId)
        } else {
          mirrorAttrs(localSrcId - masterSize)
        }
        triplet.attr = edgeAttrs(j)
        newData(j) = f (triplet)
        // println(newData(j))
      }
    }

    new GraphPartition[VD, ED2](localDstIds, masterAttrs, mirrorAttrs,
      vertexIndex, newData, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, activeSet)
  }

  def joinLocalMsgs[A: ClassTag](localMsgs: Iterator[(Int, A)])(
      vprog: (VertexId, VD, A) => VD): GraphPartition[VD, ED] = {

    val newValues = new Array[VD](masterSize)
    // Array.copy(masterAttrs, 0, newValues, 0, masterSize)

    localMsgs.foreach { msg =>
      val localVid = msg._1
      val localMsg = msg._2
      val newValue = vprog(local2global(localVid), masterAttrs(localVid), localMsg)
      if (newValue == masterAttrs(localVid)) {
        activeSet.unset(localVid)
      } else {
        activeSet.set(localVid)
      }
      newValues(localVid) = newValue
    }

    this.withNewValues(newValues).withActiveSet(activeSet)
  }

  def localLeftJoin[VD2: ClassTag](
      other: LocalFinalMessages[VD2],
      needActive: Boolean = false)
    (f: (VertexId, VD, Option[VD2]) => VD): GraphPartition[VD, ED] = {

    // val localMsgs = Array.fill[Option[VD2]](vertexAttrSize)(None)
    // println("localJoin: " + vertexAttrSize)
    val newValues = new Array[VD](masterSize)

    /*
    other.foreach { v =>
      localMsgs(v._1) = Some(v._2)
    }
    */

    // only the master vertices
    for (i <- 0 until masterSize) {
      // println(i, local2global(i), attrs(i), localMsgs(i), vertexAttrSize)
      val otherV: Option[VD2] = if (other.mask.get(i)) Some(other.values(i)) else None
      newValues (i) = f(local2global(i), masterAttrs(i), otherV)
    }

    if (needActive) {
      for (i <- 0 until masterSize) {
        if (masterAttrs (i) != newValues (i)) {
          activeSet.set (i)
        } else { // already considering the None case
          activeSet.unset (i)
        }
      }
      for (i <- masterSize until totalVertSize) {
        activeSet.unset(i)
      }
    }

    this.withNewValues(newValues).withActiveSet(activeSet)
  }

  // send mirrors to the master pids to build routing tables
  def shipMirrors: Iterator[(VertexId, Int)] = mirrorIterator.map(v => (local2global(v), v))

  // functions for undirected graphs
  def sendRequests: Iterator[(VertexId, Int)] = {
    var pos = activeSet.nextSetBit(indexStartPos)
    val requests = new BitSet(totalVertSize)
    while (pos >= indexStartPos && pos < smallDegreeEndPos) {
      // val localSrcId = pos
      val index = pos - indexStartPos
      val neighborStartPos = vertexIndex(index)
      val neighborEndPos = vertexIndex(index + 1)
      for (i <- neighborStartPos until neighborEndPos) {
        requests.set(localDstIds(i))
      }
      pos = activeSet.nextSetBit(pos + 1)
    }

    while (pos >= masterSize && pos < largeDegreeMirrorEndPos) {
      requests.set(pos)
      pos = activeSet.nextSetBit(pos + 1)
    }

    requests.iterator.map (localId => (local2global(localId), thisPid))
  }

  def syncMirrors(msgs: Iterator[(Int, VD)]): GraphPartition[VD, ED] = {
    // require(vertexAttrSize == totalVertSize) // must create spaces for all mirrors
    val totalMirrorSize = totalVertSize - masterSize
    val newValues = new Array[VD](totalMirrorSize)
    if (mirrorAttrs.length == totalMirrorSize) {
      Array.copy(mirrorAttrs, 0, newValues, 0, totalMirrorSize)
    }

    msgs.foreach { msg =>
      val localId = msg._1
      newValues(localId - masterSize) = msg._2
      activeSet.set(localId)
    }

    // println("actives: " + activeSet.iterator.length)

    this.withMirrorValues(newValues).withActiveSet(activeSet)
  }

  def getDiffVertices(initFunc: (VertexId, VD) => VD): BitSet = {
    val newBitSet = new BitSet(masterSize)
    masterAttrs.zipWithIndex.foreach { v =>
      val vid = v._2
      val vd = v._1
      val newValue = initFunc(local2global(vid), vd)
      if (newValue != vd) {
        newBitSet.set(vid)
      }
    }
    newBitSet
  }

  def generateSyncMsgsWithBitSet(bitSet: BitSet,
      useSrc: Boolean = true, useDst: Boolean = true)
  : Iterator[(Int, LocalMessageBlock[VD])] = {
    Iterator.tabulate(numPartitions) { pid =>
      val msgSize = routingTable.partitionSize(pid)
      val mirrorVids = new PrimitiveVector[Int](msgSize)
      val mirrorAttrs = new PrimitiveVector[VD](msgSize)
      var i = 0

      routingTable.foreachWithPartition(pid, useSrc, useDst) {v =>
        if (bitSet.get (v._1)) {
          // println ("Sync Messages: " + (pid, local2global (v._1), v._2))
          mirrorVids += v._2
          mirrorAttrs += masterAttrs (v._1)
          i += 1
        }
      }

      (pid, new LocalMessageBlock(mirrorVids.trim().toArray, mirrorAttrs.trim().toArray))
    }
  }

  def generateSyncMsgs(useSrc: Boolean = true, useDst: Boolean = true)
  : Iterator[(Int, LocalMessageBlock[VD])] = {
    Iterator.tabulate(numPartitions) { pid =>
      val msgSize = routingTable.partitionSize(pid)
      val mirrorVids = new PrimitiveVector[Int](msgSize)
      val mirrorAttrs = new PrimitiveVector[VD](msgSize)
      var i = 0

        routingTable.foreachWithPartition(pid, useSrc, useDst) {v =>
          if (activeSet.get (v._1)) {
            // println ("Sync Messages: " + (pid, local2global (v._1), v._2))
            mirrorVids += v._2
            mirrorAttrs += masterAttrs (v._1)
            i += 1
          }
        }

      (pid, new LocalMessageBlock(mirrorVids.trim().toArray, mirrorAttrs.trim().toArray))
    }
  }

  def generateSrcSyncMessages: Iterator[(Int, LocalMessageBlock[VD])] = {
    Iterator.tabulate(numPartitions) { pid =>
      val msgSize = routingTable.partitionSize(pid)
      val mirrorVids = new PrimitiveVector[Int](msgSize)
      val mirrorAttrs = new PrimitiveVector[VD](msgSize)

      routingTable.foreachWithPartition(pid, true, false) {v =>
        if (activeSet.get (v._1)) {
          // println ("Sync Messages: " + (pid, local2global (v._1), v._2))
          mirrorVids += v._2
          mirrorAttrs += masterAttrs (v._1)
        }
      }

      (pid, new LocalMessageBlock(mirrorVids.trim().array, mirrorAttrs.trim().array))
    }

    /*
    val messages = Array.fill(numPartitions)(new PrimitiveVector[(Int, VD)])
    for (i <- 0 until numPartitions) {
      routingTable.foreachWithPartition(i, true, false) { v =>
        if (activeSet.get(v._1)) {
          messages(i) += (v._2, attrs(v._1))
        }
      }
    }
    messages.zipWithIndex.map(m => (m._2, m._1.toArray)).iterator
    */
  }


  def syncSrcMirrors(msgs: Iterator[(Int, LocalMessageBlock[VD])]): GraphPartition[VD, ED] = {
    val newValues = new Array[VD](mirrorSize)
    msgs.flatMap(_._2.iterator).foreach { msg =>
      val mirrorLVid = msg._1 - masterSize
      newValues(mirrorLVid) = msg._2
      activeSet.set(msg._1)
    }
    // newValues.zipWithIndex.foreach(v => println(local2global(v._2), v._1))
    this.withMirrorValues(newValues).withActiveSet(activeSet)
  }


  /*
  def syncSrcMirrors(msgs: Iterator[(Int, (Int, VD))]): GraphPartition[VD, ED] = {
    val newValues = new Array[VD](vertexAttrSize)
    Array.copy(attrs, 0, newValues, 0, vertexAttrSize)
    msgs.map(_._2).foreach { msg =>
      newValues(msg._1) = msg._2
      activeSet.set(msg._1)
    }
    // newValues.zipWithIndex.foreach(v => println(local2global(v._2), v._1))
    this.withNewValues(newValues).withActiveSet(activeSet)
  }
  */

  def syncSrcAndAggregateMessages[A: ClassTag](
      msgs: Iterator[(Int, LocalMessageBlock[VD])],
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields = TripletFields.SrcWithEdge)
  : Iterator[(VertexId, A)] = {

    // val newValues = new Array[VD](vertexAttrSize)
    // Array.copy(attrs, 0, newValues, 0, vertexAttrSize)

    val startTime = System.currentTimeMillis()
    msgs.flatMap(_._2.iterator).foreach { msg =>
      mirrorAttrs(msg._1 - largeDegreeMasterEndPos) = msg._2
      activeSet.set(msg._1)
    }

    val endTime = System.currentTimeMillis()
    // println("Sync time: " + (endTime - startTime))

    val startTime2 = System.currentTimeMillis()
    val aggregates = new Array[A](local2global.length)
    val bitset = new BitSet(local2global.length)

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset, numPartitions)

    var pos = activeSet.nextSetBit(indexStartPos)
    while (pos >= indexStartPos && (pos < largeDegreeMirrorEndPos)) {
      // sdVertices += 1
      val localSrcId = pos
      val index = pos - indexStartPos
      val srcId = local2global(localSrcId)
      val dstStartPos = vertexIndex(index)
      val dstEndPos = vertexIndex(index + 1)
      val srcAttr = if (localSrcId < masterSize) {
        masterAttrs(localSrcId)
      } else {
        mirrorAttrs(localSrcId - masterSize)
      }
      // println(s"srcAttr: $srcAttr")
      for (i <- dstStartPos until dstEndPos) {
        val localDstId = localDstIds (i)
        ctx.set (srcId, local2global (localDstId), pos, localDstId,
          srcAttr, null.asInstanceOf[VD], edgeAttrs (i))
        sendMsg (ctx)
      }
      pos = activeSet.nextSetBit(pos + 1)
    }
    val endTime2 = System.currentTimeMillis()
    // println("Aggregate time: " + (endTime2 - startTime2))

    bitset.iterator.map { v => (local2global(v), aggregates(v))}
  }

  def generateLocalMsgs[A: ClassTag](
      msgs: Iterator[(VertexId, A)]
  )(reduceFunc: (A, A) => A): LocalFinalMessages[A] = {
    val newMask = new BitSet(masterSize)
    val finalMsgs = new Array[A](masterSize)

    msgs.foreach { msg =>
      val vid = msg._1
      val value = msg._2
      val pos = global2local.getOrElse(vid, -1)
      // println("From global to local: " + pos)
      if (pos >= 0) {
        if (newMask.get(pos)) {
          finalMsgs(pos) = reduceFunc(finalMsgs(pos), value)
        } else {
          newMask.set(pos)
          finalMsgs(pos) = value
        }
      }
    }
    new LocalFinalMessages(finalMsgs, newMask)
  }

  // get all the push messages and mirror messages
  // for masters in this partition
  // compute the final msgs for all local vertices
  // update mirror values
  def generateFinalMsgs[A: ClassTag](
      // msgs: Iterator[(Int, (LocalMessageBlock[VD], MessageBlock[A]))]
      msgs: Iterator[(Int, MixMessageBlock[VD, A])])(
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A): LocalFinalMessages[A] = {
  // LocalFinalMessages[A] = {

    // only need to generate local messages for masters
    val newMask = new BitSet(masterSize)
    val finalMsgs = new Array[A](masterSize)
    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, finalMsgs, newMask, numPartitions)

    val mirrorActiveSet = new BitSet(largeDegreeMirrorEndPos - largeDegreeMasterEndPos)

    var pushMsgCount = 0
    var mirrorMsgCount = 0
    var localMirrorMsgCount = 0

    // val time1 = System.currentTimeMillis()
    val mixMsgs = msgs.map(_._2)

    val time1 = System.currentTimeMillis()
    mixMsgs.foreach { msgs =>
      val pushMsgs = msgs.msgIterator
      val mirrorMsgs = msgs.syncIterator

      pushMsgs.foreach { msg =>
        val vid = msg._1
        val value = msg._2
        val pos = global2local.getOrElse(vid, -1)

        pushMsgCount += 1
        if (pos >= 0) {
          if (newMask.get(pos)) {
            finalMsgs(pos) = mergeMsg(finalMsgs(pos), value)
          } else {
            newMask.set(pos)
            finalMsgs(pos) = value
          }
        }
      }

      mirrorMsgs.foreach { msg =>
        val lvid = msg._1 - masterSize
        mirrorAttrs(lvid) = msg._2
        mirrorActiveSet.set(lvid)
      }
    }

    val time2 = System.currentTimeMillis()
    // println("push msg time: " + (time2 - time1))

    // handle all the LDmirrors, including the mixMasters
    // (mirrorMsgs ++ activeLDMasters).foreach { msg =>
    // only process mirrorMsgs in this step

    var pos = mirrorActiveSet.nextSetBit(0)

    while (pos >= 0) {
      val mirrorLVid = pos + masterSize
      val value = mirrorAttrs(pos)
      val index = mirrorLVid - indexStartPos

      val neighborStartPos = vertexIndex(index)
      val neighborEndPos = vertexIndex(index + 1)
      val srcId = local2global(mirrorLVid)
      // val srcAttr = attrs(localId)
      // ctx.setSrcOnly(srcId, localId, value)

      for (i <- neighborStartPos until neighborEndPos) {
        val edgeAttr = edgeAttrs(i)
        val localDstId = localDstIds(i)
        // println("EdgeAttr: " + edgeAttr)
        val dstAttr = masterAttrs(localDstId)
        ctx.set(srcId, local2global(localDstId),
          mirrorLVid, localDstId, value, dstAttr, edgeAttr)
        mirrorMsgCount += 1
        // println(localId, localDstIds(i), edgeAttr)
        sendMsg (ctx)
      }

      pos = mirrorActiveSet.nextSetBit(pos + 1)
    }

    val time3 = System.currentTimeMillis()
    // println("mirror sync time: " + (time3 - time2))

    new LocalFinalMessages(finalMsgs, newMask)

    /*
    // to make sure memory can be collected as soon as msgs are processed
    val mirrorMsgs = mixMsgs.flatMap(_.syncIterator)
    val pushMsgs = mixMsgs.flatMap(_.msgIterator).iterator

    // val sortedMirrorMsgs = mirrorMsgs.sortBy(_._1)
    val time1_5 = System.currentTimeMillis()
    println("Time to get messages: " + (time1_5 - time1))

    // println("LDMaster size: " + activeLDMasters.length)
    // println("Mirror size: " + mirrorMsgs.length)

    mirrorMsgs.foreach { msg =>
      val lvid = msg._1 - masterSize
      mirrorAttrs(lvid) = msg._2
      mirrorActiveSet.set(lvid)
    }

    // handle all the LDmirrors, including the mixMasters
    // (mirrorMsgs ++ activeLDMasters).foreach { msg =>
    // only process mirrorMsgs in this step

    var pos = mirrorActiveSet.nextSetBit(0)

    while (pos >= 0) {
      val mirrorLVid = pos + largeDegreeMasterEndPos
      val value = mirrorAttrs(pos)
      val index = mirrorLVid - indexStartPos

      val neighborStartPos = vertexIndex(index)
      val neighborEndPos = vertexIndex(index + 1)
      val srcId = local2global(mirrorLVid)
      // val srcAttr = attrs(localId)
      // ctx.setSrcOnly(srcId, localId, value)

      for (i <- neighborStartPos until neighborEndPos) {
        val edgeAttr = edgeAttrs(i)
        val localDstId = localDstIds(i)
        // println("EdgeAttr: " + edgeAttr)
        ctx.set(srcId, local2global(localDstId),
          mirrorLVid, localDstId, value, null.asInstanceOf[VD], edgeAttr)
        mirrorMsgCount += 1
        // println(localId, localDstIds(i), edgeAttr)
        sendMsg (ctx)
      }

      pos = mirrorActiveSet.nextSetBit(pos + 1)
    }

    /*
    sortedMirrorMsgs.foreach { msg =>
      val localSrcId = msg._1
      if (localSrcId < largeDegreeMasterEndPos) {
        println("Error here!")
      }
      val value = msg._2
      val index = localSrcId - indexStartPos

      val neighborStartPos = vertexIndex(index)
      val neighborEndPos = vertexIndex(index + 1)
      val srcId = local2global(localSrcId)
      // val srcAttr = attrs(localId)
      // ctx.setSrcOnly(srcId, localId, value)

      for (i <- neighborStartPos until neighborEndPos) {
        val edgeAttr = edgeAttrs(i)
        val localDstId = localDstIds(i)
        // println("EdgeAttr: " + edgeAttr)
        ctx.set(srcId, local2global(localDstId),
          localSrcId, localDstId, value, null.asInstanceOf[VD], edgeAttr)
        mirrorMsgCount += 1
        // println(localId, localDstIds(i), edgeAttr)
        sendMsg (ctx)
      }
    }
    */

    val time2 = System.currentTimeMillis()
    println("mirror sync time: " + (time2 - time1_5))

    pushMsgs.foreach { msg =>
      val vid = msg._1
      val value = msg._2
      val pos = global2local.getOrElse(vid, -1)

      pushMsgCount += 1
      if (pos >= 0) {
        if (newMask.get(pos)) {
          finalMsgs(pos) = mergeMsg(finalMsgs(pos), value)
        } else {
          newMask.set(pos)
          finalMsgs(pos) = value
        }
      }
    }

    val time3 = System.currentTimeMillis()
    println("push msg time: " + (time3 - time2))
    // handle the mixMasters
    // println("LDMEPos: " + largeDegreeMirrorEndPos + " " + vertexIndex.size)

    /*
    println("totalMessages: " + pushMsgCount, mirrorMsgCount, localMirrorMsgCount
      + " vertice num: " + this.smallDegreeEndPos,
      (this.vertexIndex.size - 1 - this.largeDegreeMirrorEndPos),
      (this.largeDegreeMasterEndPos - this.largeDegreeMirrorEndPos),
      this.vertexIndex.size - 1, this.largeDegreeMasterEndPos)
      */
      * */
  }

  def localAggregate[A: ClassTag](localMsgs: Iterator[(Int, A)])(
    reduceFunc: (A, A) => A): Iterator[(Int, A)] = {
    // also consider mirrors, may have some redundant spaces,
    // but do not have to judge whether master or not
    val attrSize = largeDegreeMasterEndPos
    val newValues = new Array[A](attrSize)
    // Array.copy(attrs, 0, newValues, vertexAttrSize, vertexAttrSize)
    val newMask = new BitSet(attrSize)

    localMsgs.foreach { product =>
      val vid = product._1
      val vdata = product._2
      // val pos = vid
      if (vid >= 0) {
        // println(vid)
        if (newMask.get (vid)) {
          newValues (vid) = reduceFunc (newValues (vid), vdata)
        } else { // otherwise just store the new value
          newMask.set (vid)
          newValues (vid) = vdata
        }
        //        println("debug")
      }
    }

    // newMask.iterator.map(v => (local2global(v), newValues(v)))
    newValues.zipWithIndex.filter(v => v._2 < smallDegreeEndPos || v._2 >= largeDegreeMirrorEndPos)
      .map(v => (v._2, v._1)).iterator
  }

  def globalAggregate[A: ClassTag](localMsgs: Iterator[(Int, A)])(
      reduceFunc: (A, A) => A): Iterator[(VertexId, A)] =
    localAggregate(localMsgs)(reduceFunc).map(msg => (local2global(msg._1), msg._2))




  /*
  def joinLocalMsgs[A: ClassTag](localMsgs: (Array[A], BitSet))(
      vprog: (VertexId, VD, A) => VD): GraphPartition[VD, ED] = {

    val uf = (id: VertexId, data: VD, o: Option[A]) => {
      o match {
        case Some (u) => vprog (id, data, u)
        case None => data
      }
    }

    val mask = localMsgs._2
    val values = localMsgs._1
    val newValues = new Array[VD](vertexAttrSize)
    for (i <- 0 until vertexAttrSize) {
      val otherV: Option[A] = if (mask.get (i)) Some (values (i)) else None
      newValues (i) = uf (local2global (i), attrs (i), otherV)
      if (attrs (i) == newValues (i)) {
        activeSet.unset (i)
      } else {
        activeSet.set (i)
      }
    }
    this.withNewValues(newValues)
  }
  */
  // edge centric computation
  /*
  def aggregateMessagesForBothSizes[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A): Iterator[(VertexId, A)] = {
    val aggregates = new Array[A](local2global.length)
    val bitset = new BitSet(local2global.length)

    // val mirrorMsgs = new Array[PrimitiveVector[(VertexId, VD)]](numPartitions)

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset, numPartitions)

    // for all the active edges
    var pos = activeSet.nextSetBit(0)
    while (pos >= 0 && pos < largeDegreeStartPos) {
      val srcId = local2global(pos)
      val dstStartPos = vertexIndex(pos)
      val dstEndPos = vertexIndex(pos + 1)
      val srcAttr = attrs(pos)
      for (i <- dstStartPos until dstEndPos) {
        val localDstId = localDstIds (i)
        ctx.set (srcId, local2global (localDstId), pos, localDstId,
          srcAttr, attrs(i), edgeAttrs (i))
        sendMsg (ctx)
      }
      pos = activeSet.nextSetBit(pos + 1)
    }

    // generate mirror msgs
    bitset.iterator.map { localId =>
      (global2local(localId), aggregates(localId))
    }
  }
*/
  // considering vertices from both sides of each edge
  def aggregateMessagesEdgeCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields = TripletFields.BothSidesWithEdge): Iterator[(VertexId, A)] = {
    val aggregates = new Array[A](local2global.length)
    val bitset = new BitSet (local2global.length)

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset, numPartitions)

    for (i <- 0 until vertexIndex.size - 1) {
      // val clusterPos = vertexIndex (i)
      val clusterLocalSrcId = i + indexStartPos
      val scanCluster =
        if (edgeDirection == EdgeDirection.Out) isActive (clusterLocalSrcId)
        else if (edgeDirection == EdgeDirection.Both) true
        else if (edgeDirection == EdgeDirection.In) true
        else throw new Exception ("unreachable")

      if (scanCluster) {
        // println("scan the cluster")
        val startPos = vertexIndex (i)
        val endPos = vertexIndex (i + 1)
        val srcAttr = if (tripletFields.useSrc == false) {
          null.asInstanceOf[VD]
        } else {
          if (clusterLocalSrcId >= masterSize) {
            mirrorAttrs (clusterLocalSrcId - masterSize) // mirrors
          } else {
            masterAttrs (clusterLocalSrcId) // masters
          }
        }

        // println("srcAttr: " + srcAttr)
        ctx.setSrcOnly (local2global (clusterLocalSrcId), clusterLocalSrcId, srcAttr)
        for (i <- startPos until endPos) {
          val localDstId = localDstIds (i)
          val dstId = local2global (localDstId)
          val edgeIsActive =
            if (edgeDirection == EdgeDirection.In) isActive (localDstId)
            else if (edgeDirection == EdgeDirection.Both) isActive (localDstId) ||
              isActive (clusterLocalSrcId)
            else if (edgeDirection == EdgeDirection.Out) true
            else throw new Exception ("unreachable")
          if (edgeIsActive) {
            // println("send messages")
            val dstAttr = if (localDstId >= masterSize + mirrorSize || tripletFields.useDst == false) {
              // println(localDstId, mirrorSize)
              null.asInstanceOf[VD]
            } else {
              if (localDstId >= masterSize) {
                mirrorAttrs(localDstId - masterSize)
              }
              else {
                masterAttrs (localDstId)
              }
            }

            // println("dstAttr: " + dstAttr)
            ctx.set (local2global (clusterLocalSrcId), dstId,
              clusterLocalSrcId, localDstId, srcAttr, dstAttr, edgeAttrs (i))
            // println("ctx: " + ctx.srcAttr, ctx.dstAttr)
            sendMsg (ctx)
          }
        }
      }
    }

    bitset.iterator.map { localId => (local2global(localId), aggregates(localId)) }
  }

  def aggregateMessagesVertexCentric2[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields = TripletFields.SrcWithEdge)
  : Iterator[(VertexId, A)] = {
    val aggregates = new Array[A](local2global.length)
    val bitset = new BitSet(local2global.length)

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset, numPartitions)

    var pos = activeSet.nextSetBit(indexStartPos)
    while (pos >= indexStartPos && (pos < vertexIndex.size + indexStartPos - 1)) {
      // sdVertices += 1
      val localSrcId = pos
      val index = pos - indexStartPos
      val srcId = local2global(localSrcId)
      val dstStartPos = vertexIndex(index)
      val dstEndPos = vertexIndex(index + 1)
      val srcAttr = masterAttrs(localSrcId)
      for (i <- dstStartPos until dstEndPos) {
        val localDstId = localDstIds (i)
        ctx.set (srcId, local2global (localDstId), pos, localDstId,
          srcAttr, null.asInstanceOf[VD], edgeAttrs (i))
        sendMsg (ctx)
      }
      pos = activeSet.nextSetBit(pos + 1)
    }

    bitset.iterator.map { v => (local2global(v), aggregates(v))}
  }

  // msgs: First the aggregated msgs for remote vertices, then the mirror messages


  def aggregateMessagesVertexCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      tripletFields: TripletFields = TripletFields.SrcWithEdge)
  : Iterator[(Int, MixMessageBlock[VD, A])] = {
    val aggregates = new Array[A](local2global.length)
    val bitset = new BitSet(local2global.length)
    var sdVertices = 0
    var ldVertices = 0
    val startPushTime = System.currentTimeMillis()

    val ctx = new AggregatingVertexContext[VD, ED, A](mergeMsg, aggregates, bitset, numPartitions)
    // first generate the push msgs, only for smallDegreeVertices
    // which are out-completed
    var pos = activeSet.nextSetBit(indexStartPos)
    while ((pos >= indexStartPos) && (pos < masterSize)) {
      sdVertices += 1
      val index = pos - indexStartPos
      val dstStartPos = vertexIndex(index)
      val dstEndPos = vertexIndex(index + 1)
      val srcId = local2global(pos)
      val srcAttr = masterAttrs(pos)
      for (i <- dstStartPos until dstEndPos) {
        val localDstId = localDstIds (i)
        val dstAttr = if (localDstId >= masterSize + mirrorSize) {
          // println(localDstId, mirrorSize)
          null.asInstanceOf[VD]
        } else {
          if (localDstId >= masterSize) {
            mirrorAttrs(localDstId - masterSize)
          }
          else {
            masterAttrs (localDstId)
          }
        }
        // println("dstAttr: " + dstAttr)
        ctx.set (srcId, local2global (localDstId), pos, localDstId,
          srcAttr, dstAttr, edgeAttrs (i))
        sendMsg (ctx)
      }
      pos = activeSet.nextSetBit(pos + 1)
    }
    val endPushTime = System.currentTimeMillis()
    // println("Push Time: " + (endPushTime - startPushTime))

    // try to reduce the space used for storing push messages
    val totalMsgSize = bitset.iterator.length
    val avgSize = totalMsgSize / numPartitions
    val startTime = System.currentTimeMillis()
    val pushVids = Array.fill(numPartitions)(
       new PrimitiveVector[VertexId](2 * avgSize + 1))
    val pushMsgs = Array.fill(numPartitions)(new PrimitiveVector[A](2 * avgSize + 1))
    val partitioner = new HashPartitioner(numPartitions)
    bitset.iterator.foreach { localId =>
      val globalId = local2global(localId)
      val pid = partitioner.getPartition(globalId)
      // println(globalId, aggregates(localId))
      pushVids(pid) += globalId
      pushMsgs(pid) += aggregates(localId)
    }

    // println("Process Push Msgs: " + (System.currentTimeMillis() - startTime))
    /*

    if (tripletFields.useSrc == false) {

      pushVids.zip(pushMsgs).zipWithIndex.map { msgs =>
        // (msgs._2, (new LocalMessageBlock(Array.empty[Int], Array.empty[VD]),
        //   new MessageBlock(msgs._1._1.trim.array, msgs._1._2.trim.array)))
        (msgs._2, new MixMessageBlock(Array.empty[Int], Array.empty[VD], msgs._1._1.trim.array,
          msgs._1._2.trim.array))
      }.iterator
    } else {
    */

      // generate mirror msgs, for each active LDMaster

      // var mirrorVids = new PrimitiveVector[Int]
      // var mirrorAttrs = new PrimitiveVector[VD]
      val msgs = Iterator.tabulate(numPartitions) { pid =>
        val mirrorVids = new PrimitiveVector[Int]
        val mirrorAttrs = new PrimitiveVector[VD]
        // val mirrorMsgs = new PrimitiveVector[(Int,VD)](routingTable.partitionSize(pid))

        routingTable.foreachWithPartition(pid, true, false) { vid =>
          if (activeSet.get (vid._1)) {
            ldVertices += 1
            // determine whether this ldmaster is active or not
            mirrorVids += vid._2
            mirrorAttrs += masterAttrs (vid._1)
            // mirrorMsgs += (vid._2, masterAttrs(vid._1))
          }
        }

        // (pid, (new LocalMessageBlock(mirrorVids.trim().array, mirrorAttrs.trim().array),
        //   new MessageBlock(pushVids(pid).trim().array, pushMsgs(pid).trim().array)))

        (pid, new MixMessageBlock(
            mirrorVids.trim().toArray,
            mirrorAttrs.trim().toArray,
            pushVids(pid).trim().toArray,
            pushMsgs(pid).trim().toArray))
        // (pid, new MixMessageBlock(mirrorMsgs.iterator, pushMsgs(pid).iterator))
      }

      // println(s"ldVertices: $ldVertices, sdVertices: $sdVertices")
      msgs
    }
  // }

  def withNewValues(newValues: Array[VD]): GraphPartition[VD, ED] = {
    new GraphPartition[VD, ED](localDstIds, newValues, mirrorAttrs,
      vertexIndex, edgeAttrs, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, activeSet)
  }

  def withMirrorValues(newValues: Array[VD]): GraphPartition[VD, ED] = {
    new GraphPartition[VD, ED](localDstIds, masterAttrs, newValues,
      vertexIndex, edgeAttrs, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, activeSet)
  }

  def withActiveSet(newActiveSet: BitSet): GraphPartition[VD, ED] = {
    new GraphPartition[VD, ED](localDstIds, masterAttrs, mirrorAttrs,
      vertexIndex, edgeAttrs, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, routingTable, newActiveSet)
  }

  def withRoutingTable(newRoutingTable: RoutingTable): GraphPartition[VD, ED] = {
    new GraphPartition[VD, ED](localDstIds, masterAttrs, mirrorAttrs,
      vertexIndex, edgeAttrs, global2local, local2global, indexStartPos,
      smallDegreeEndPos, largeDegreeMirrorEndPos, largeDegreeMasterEndPos,
      numPartitions, newRoutingTable, activeSet)
  }
}

private class SimpleVertexContext[VD, ED, A]()

private class AggregatingVertexContext[VD, ED, A](
    mergeMsg: (A, A) => A,
    aggregates: Array[A],
    bitset: BitSet,
    numPartitions: Int,
    direction: EdgeDirection = EdgeDirection.Out) extends VertexContext[VD, ED, A](direction) {

  private[this] var _srcId: VertexId = _
  private[this] var _dstId: VertexId = _
  private[this] var _localSrcId: Int = _
  private[this] var _localDstId: Int = _
  private[this] var _srcAttr: VD = _
  private[this] var _dstAttr: VD = _
  private[this] var _attr: ED = _

  def set(
      srcId: VertexId, dstId: VertexId,
      localSrcId: Int, localDstId: Int,
      srcAttr: VD, dstAttr: VD, attr: ED) {
    _srcId = srcId
    _dstId = dstId
    _localSrcId = localSrcId
    _localDstId = localDstId
    _srcAttr = srcAttr
    _dstAttr = dstAttr
    _attr = attr
  }

  def setSrcOnly(srcId: VertexId, localSrcId: Int, srcAttr: VD) {
    _srcId = srcId
    _localSrcId = localSrcId
    _srcAttr = srcAttr
  }

  def setEdgeAndDst(edgeAttr: ED, localDstId: Int, dstId: VertexId): Unit = {
    _attr = edgeAttr
    _localDstId = localDstId
    _dstId = dstId
  }

  def setRest(dstId: VertexId, localDstId: Int, dstAttr: VD, attr: ED) {
    _dstId = dstId
    _localDstId = localDstId
    _attr = attr
    _dstAttr = dstAttr
  }

  override def srcId: VertexId = _srcId

  override def dstId: VertexId = _dstId

  override def srcAttr: VD = _srcAttr

  override def dstAttr: VD = _dstAttr

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
      // println(localId, msg)
      bitset.set (localId)
    }
  }
}
