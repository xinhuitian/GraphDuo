
package org.apache.spark.graphv.enhanced

import org.apache.spark.graphv._
import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.util.collection.{BitSet, PrimitiveVector}

object RoutingTable {
  /**
   * masterRoutingMessage: mirror partitions send the (master, partitionID) kv pairs
   * mirrorRoutingMessage: master partitions sync the (master, mirror) msg with mirror partitions
   */
  type RoutingMessage = (VertexId, (PartitionID, Int))

  private def toMessage(
      vid: VertexId,
      localId: Int,
      pid: PartitionID,
      position: Byte): RoutingMessage = {
    val positionUpper2 = position << 30
    val pidLower30 = pid & 0x3FFFFFFF
    (vid, (positionUpper2 | pidLower30, localId))
    // (vid, (pid, localId))
  }

  private def vidFromMessage(msg: RoutingMessage): VertexId = msg._1
  private def pidFromMessage(msg: RoutingMessage): PartitionID = msg._2._1 & 0x3FFFFFFF
  private def positionFromMessage(msg: RoutingMessage): Byte = (msg._2._1 >> 30).toByte
  private def localIdFromMessage(msg: RoutingMessage): Int = msg._2._2
  // type mirrorRoutingMessage = (Int, (Int, PartitionID))

  def generateMessagesWithPos(
      pid: PartitionID,
      graphPartition: GraphPartition[_, _],
      needSrc: Boolean = true,
      needDst: Boolean = true)
  : Iterator[RoutingMessage] = {

    val masterSize = graphPartition.masterSize
    val srcMirrorSize = graphPartition.largeDegreeMirrorEndPos
    - graphPartition.largeDegreeMasterEndPos
    val lDMasterEPos = graphPartition.largeDegreeMasterEndPos
    val pureOutMirrorSize = graphPartition.local2global.length
    - graphPartition.largeDegreeMirrorEndPos
    // println(mirrorSize + " " + lDMirrorSPos + " " + lDMirrorEPos + " " + lDMasterEPos)
    var map = Array.fill(srcMirrorSize)(0x0.toByte)
    val local2global = graphPartition.local2global

    // start from smallDegreeEndPos
    if (needDst) {
      map = Array.fill(local2global.length - masterSize)(0x0.toByte)
      // start from LDMasterEPos
      graphPartition.remoteOutMirrorIterator.foreach { v =>
        val index = v - masterSize
        // println("Remote Out Mirrors: " + v)
        map(index) = (map(index) | 0x2).toByte
      }
    }

    if (needSrc) {
      graphPartition.remoteLDMirrorIterator.foreach { v =>
        val index = v - masterSize
        // println("Remote In Mirrors: " + v)
        // val prev = map(index)
        map(index) = (map(index) | 0x1).toByte
      }
    }

    map.zipWithIndex.filter(_._1 != 0x0.toByte).map { v =>
      val localVid = v._2 + masterSize
      // println(localVid + " " + pid + " " + v._1 + " " + local2global(localVid))

      // (globalVid, mirrorLocalId, mirrorPid, mirrorPos)
      val msg = toMessage(local2global(localVid), localVid, pid, v._1)
      // println(msg)
      msg
    }.iterator
  }

  // for BiGraphs?
  /*
  def generateMessagesWithoutPos(pid: PartitionID, graphPartition: GraphPartition[_, _])
  : Iterator[RoutingMessage] = {
    val local2global = graphPartition.local2global
    graphPartition.mirrorIterator.map { v =>
      toMessage(local2global(v._1), v._1, pid, 0x0.toByte)
    }
  }
  */

  // def fromLocalMirrors()

  // each partition get the routing messages,
  def fromMsgs(numPartitions: Int, iter: Iterator[RoutingMessage],
      g2lMap: GraphXPrimitiveKeyOpenHashMap[VertexId, Int]):
  RoutingTable = {
    val pid2vid = Array.fill(numPartitions)(new PrimitiveVector[(Int, Int)])
    val srcFlags = Array.fill(numPartitions)(new PrimitiveVector[Boolean])
    val dstFlags = Array.fill(numPartitions)(new PrimitiveVector[Boolean])

    for (msg <- iter) {
      val pid = pidFromMessage(msg)
      val localVid = (g2lMap(vidFromMessage(msg)), localIdFromMessage(msg))
      val position = positionFromMessage(msg)
      // println("Routing Message: " + pid + " " + localVid + " " + (position & 0x1))
      pid2vid(pid) += localVid
      srcFlags(pid) += (position & 0x1) != 0
      dstFlags(pid) += (position & 0x2) != 0
    }

    new RoutingTable(pid2vid.zipWithIndex.map {
      case (vids, pid) => (vids.trim().array, toBitSet(srcFlags(pid)), toBitSet(dstFlags(pid)))
    })
  }

  private def toBitSet(flags: PrimitiveVector[Boolean]): BitSet = {
    val bitset = new BitSet(flags.size)
    var i = 0
    while (i < flags.size) {
      if (flags(i)) {
        bitset.set(i)
      }
      i += 1
    }
    bitset
  }
}

class RoutingTable(
    // masterLVid to mirrorLVid
    // mirrors, only record the out neighbors
    // (localMaster, localMirror)
    private val routingTable: Array[(Array[(Int, Int)], BitSet, BitSet)]
) extends Serializable {
  val numPartitions: Int = routingTable.length

  def partitionSize(pid: PartitionID): Int = routingTable(pid)._1.length

  def iterator: Iterator[(Int, Int)] = routingTable.iterator.flatMap(_._1.iterator)

  def foreachWithPartition
    (pid: PartitionID, includeSrc: Boolean, includeDst: Boolean)
    (f: ((Int, Int)) => Unit): Unit = {

    val (vidsCandidate, srcVids, dstVids) = routingTable(pid)

    if (includeSrc && includeDst) {
      // Avoid checks for performance
      vidsCandidate.iterator.foreach(f)
    } else if (!includeSrc && !includeDst) {
      // Do nothing
    } else {
      val relevantVids = if (includeSrc) srcVids else dstVids
      relevantVids.iterator.foreach (i => f(vidsCandidate(i)))
    }
  }
}

class finalRoutingTable(
    private val routingTable: Array[Array[(Int, Int)]],        // masterLVid to mirrorLVid
    private val reverseRoutingTable: Array[Array[(Int, Int)]] // mirrorLVid to masterLVid
) extends Serializable {


}
