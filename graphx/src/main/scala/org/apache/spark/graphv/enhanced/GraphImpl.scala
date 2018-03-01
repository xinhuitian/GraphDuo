
package org.apache.spark.graphv.enhanced

import scala.reflect.ClassTag

import org.apache.spark.graphv._
import org.apache.spark.graphv.util.GraphVAppendOnlyMap
import org.apache.spark.HashPartitioner
import org.apache.spark.internal.Logging
import org.apache.spark.OneToOneDependency
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.collection.{CompactBuffer, OpenHashSet}

class GraphImpl[VD: ClassTag, ED: ClassTag] protected(
    @transient val partitionsRDD: RDD[(Int, GraphPartition[VD, ED])],
    val targetStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY)
  extends Graph[VD, ED](partitionsRDD.context,
    List (new OneToOneDependency (partitionsRDD))) with Serializable with Logging {

  override def getActiveVertexNums: Long = partitionsRDD.mapPartitions { iter =>
    val (pid, part) = iter.next()
    // println(part.getActiveNum)
    Iterator(part.getActiveNum)
  }.reduce(_ + _)

  override def getActiveEdgeNums: Long = partitionsRDD.mapPartitions { iter =>
    iter.map(_._2.getActiveEdgeNum)
  }.reduce(_ + _)

  override def getTotalMsgs: Long = partitionsRDD.mapPartitions { iter =>
    iter.map(_._2.getTotalMsgs)
  }.reduce(_ + _)

  override def getTotalNoDupMsgs: Long = partitionsRDD.mapPartitions { iter =>
    iter.map(_._2.getTotalNoDupMsgs)
  }.reduce(_ + _)

  override def activateAllMasters: GraphImpl[VD, ED] = {
    this.withPartitionsRDD(partitionsRDD.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      Iterator((pid, part.activateAllMasters))
    })
  }

  override def vertices: RDD[(VertexId, VD)] = {
    partitionsRDD.mapPartitions(partIter => partIter.next()._2.iterator)
  }

  override def edges: RDD[Edge[ED]] = {
    partitionsRDD.mapPartitions(partIter => partIter.next()._2.edgeIterator)
  }

  override def edgeSize: Int = partitionsRDD
    .mapPartitions(partIter => Iterator(partIter.next()._2.edgeSize))
    .sum().toInt

  override def mapVertices[VD2: ClassTag](
      f: (VertexId, VD) => VD2,
      needActive: Boolean = false): Graph[VD2, ED] = {
    val newParts = partitionsRDD.map { part =>
      val pid = part._1
      val newPart = part._2.mapVertices(f, needActive)
      (pid, newPart)
    }
    new GraphImpl(newParts)
  }

  override def mapMirrors(f: (VertexId, VD) => VD): Graph[VD, ED] = {
    val newParts = partitionsRDD.map { part =>
      val pid = part._1
      val newPart = part._2.mapMirrors(f)
      (pid, newPart)
    }
    new GraphImpl(newParts)
  }

  override def mapEdges[ED2: ClassTag](map: Edge[ED] => ED2): Graph[VD, ED2] = {
    this.withPartitionsRDD(partitionsRDD.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      Iterator((pid, part.mapEdges(map)))
    })
  }

  override def mapTriplets[ED2: ClassTag](
      func: GraphVEdgeTriplet[VD, ED] => ED2,
      tripletFields: TripletFields): GraphImpl[VD, ED2] = {

    var newGraph = this
    // println("Whether use src: " + tripletFields.useSrc)
    if (tripletFields.useSrc) {
      println("Use Src")
      newGraph = this.syncSrcMirrors
      // newGraph.edges.foreach(println)
    }

    this.withPartitionsRDD(newGraph.partitionsRDD.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      Iterator((pid, part.mapTriplets(func)))
    })
  }

  override val partitioner =
    partitionsRDD.partitioner.orElse(Some(new HashPartitioner(partitions.length)))

  override def cache(): this.type = {
    partitionsRDD.persist()
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

  override def syncSrcMirrors: GraphImpl[VD, ED] = {
    val syncMsgs = partitionsRDD.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      part.generateSrcSyncMessages
    }.partitionBy(partitioner.get).cache()

    // syncMsgs.foreach(_._2.iterator.foreach(println))

    this.withPartitionsRDD(partitionsRDD.zipPartitions(syncMsgs) {
      (partIter, msgIter) =>
      val (pid, part) = partIter.next()
      Iterator((pid, part.syncSrcMirrors(msgIter)))
    })
  }

  /*
  def syncMirrors: GraphImpl[VD, ED] = {
    val syncMsgs = partitionsRDD.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      part.generateSyncMsgs

    }
  }
  */

  def joinLocalMsgs[A: ClassTag](localMsgs: RDD[(Int, A)])(
      vFunc: (VertexId, VD, A) => VD): RDD[(VertexId, VD)] = {

    partitionsRDD.zipPartitions(localMsgs) { (partIter, localMsgs) =>
      val (pid, part) = partIter.next()
      part.joinLocalMsgs(localMsgs)(vFunc).masterIterator
    }
  }

  /*
  def localAggregate[A: ClassTag](localMsgs: RDD[(Int, A)])(
      reduceFunc: (A, A) => A): RDD[(Int, A)] = {

    partitionsRDD.zipPartitions(localMsgs) { (partIter, msgs) =>
      val (_, part) = partIter.next()
      part.localAggregate(msgs)(reduceFunc)
    }
  }


  def globalAggregate[A: ClassTag](localMsgs: RDD[LocalFinalMessages[A]])(
      reduceFunc: (A, A) => A): RDD[(VertexId, A)] = {

    partitionsRDD.zipPartitions(localMsgs) { (partIter, msgs) =>
      val (_, part) = partIter.next()
      part.globalAggregate(msgs)(reduceFunc)
    }
  }
  */

  override def aggregateGlobalMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true
  ): RDD[(VertexId, A)] = {
    aggregateMessages(sendMsg, mergeMsg,
      edgeDirection, tripletFields, needActive)
      .zipPartitions(partitionsRDD) { (msgIter, partIter) =>
      val (_, part) = partIter.next()
      msgIter.flatMap (msgs => msgs.iterator.map { m => (part.local2global(m._1), m._2)})
    }
    // globalAggregate(preAgg)(mergeMsg)
  }

  override def aggregateLocalMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true
  ): RDD[(Int, A)] = {
    aggregateMessages(sendMsg, mergeMsg,
      edgeDirection, tripletFields, needActive)
      .mapPartitions { msgIter =>
        msgIter.flatMap (_.iterator) }
  }

  override def aggregateMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true
  ): RDD[LocalFinalMessages[A]] = {
    edgeDirection match {
      case EdgeDirection.Out =>
        aggregateMessagesVertexCentric (sendMsg, mergeMsg,
          edgeDirection, tripletFields, needActive)
      case _ =>
        aggregateMessagesEdgeCentric (sendMsg, mergeMsg,
          edgeDirection, tripletFields, needActive)
      // case _ => throw new IllegalArgumentException("Not reachable")
    }
  }


  def aggregateMessagesVertexCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true): RDD[LocalFinalMessages[A]] = {
    val preAgg = partitionsRDD.mapPartitions { partIter =>
      val (pid, graphPart) = partIter.next()
      graphPart.aggregateMessagesVertexCentric(sendMsg, mergeMsg, tripletFields)
    }.partitionBy(new HashPartitioner(this.getNumPartitions)).cache()

    partitionsRDD.zipPartitions(preAgg) { (parts, aggMsgs) =>
      val (pid, part) = parts.next()
      Iterator(part.generateFinalMsgs(aggMsgs)(sendMsg, mergeMsg))
      // localMsgs.iterator
      // Iterator((pid, part.joinLocalMsgs(localMsgs)(vFunc)))
    }
  }

  def aggregateMessagesEdgeCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true): RDD[LocalFinalMessages[A]] = {
    println("numPartitions: " + this.getNumPartitions)
    val preAgg = partitionsRDD.mapPartitions { partIter =>
      val (pid, graphPart) = partIter.next()
      val msgs = graphPart.aggregateMessagesEdgeCentric(sendMsg,
        mergeMsg, edgeDirection, tripletFields)
      // msgs.foreach(println)
      msgs
    }.partitionBy(new HashPartitioner(this.getNumPartitions))

    partitionsRDD.zipPartitions(preAgg) { (parts, aggMsgs) =>
      val (pid, part) = parts.next()
      // aggMsgs.foreach(println)
      Iterator(part.generateLocalMsgs(aggMsgs)(mergeMsg))
      // localMsgs.iterator
      // Iterator((pid, part.joinLocalMsgs(localMsgs)(vFunc)))
    }
  }

  override def compute[A: ClassTag](
    sendMsg: VertexContext[VD, ED, A] => Unit,
    mergeMsg: (A, A) => A,
    vFunc: (VertexId, VD, A) => VD,
    edgeDirection: EdgeDirection,
    tripletFields: TripletFields,
    needActive: Boolean = false): Graph[VD, ED] = {
    edgeDirection match {
      case EdgeDirection.Out =>
        computeVertexCentric (sendMsg, mergeMsg, vFunc,
          edgeDirection, needActive)
      case _ =>
        computeEdgeCentric (sendMsg, mergeMsg, vFunc,
          edgeDirection, needActive)
    }
  }

  override def compute2[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      // tripletFields: TripletFields,
      needActive: Boolean = false,
      edgeFilter: Boolean = false): Graph[VD, ED] = {
    edgeDirection match {
      case EdgeDirection.Out =>
        if (edgeFilter == false) {
          computeVertexCentric (sendMsg, mergeMsg, vFunc,
            edgeDirection, needActive)
        } else {
          computeEdgeCentric (sendMsg, mergeMsg, vFunc,
            edgeDirection, needActive, true, true)
        }

      case _ =>
        computeEdgeCentric (sendMsg, mergeMsg, vFunc,
          edgeDirection, needActive)
    }
  }

  def computeVertexCentric2[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = false): Graph[VD, ED] = {

    partitionsRDD.cache()
    var time1 = System.currentTimeMillis()
    val syncMessages = partitionsRDD.mapPartitions { partIter =>
      val (pid, graphPart) = partIter.next()
      graphPart.generateSrcSyncMessages
    }.partitionBy(new HashPartitioner(this.getNumPartitions)).cache()

    syncMessages.count()
    var time2 = System.currentTimeMillis()
    println("Time For mirror sync: " + (time2 - time1))

    val preAgg = partitionsRDD.zipPartitions(syncMessages) {
      (partIter, syncMsgs) =>
      val (pid, part) = partIter.next()
      part.syncSrcAndAggregateMessages(syncMsgs, sendMsg, mergeMsg, tripletFields)
    }.partitionBy(new HashPartitioner(this.getNumPartitions)).cache()

    preAgg.count()
    time1 = System.currentTimeMillis()
    println("Time for message aggre: " + (time1 - time2))

    /*
    val preAgg = newPartitionsRDD.mapPartitions { partIter =>
      val (pid, graphPart) = partIter.next()
      // println("AggregateMessageVertexCentric")
      graphPart.aggregateMessagesVertexCentric2(sendMsg, mergeMsg, tripletFields)
    }.partitionBy(new HashPartitioner(this.getNumPartitions))
    */

    val uf = (id: VertexId, data: VD, o: Option[A]) => {
      o match {
        case Some (u) => vFunc (id, data, u)
        case None => data
      }
    }

    val finalPartitionsRDD = partitionsRDD.zipPartitions(preAgg) {
      (partIter, aggMsgs) =>
        val (pid, part) = partIter.next()
        val localMsgs = part.generateLocalMsgs(aggMsgs)(mergeMsg)
        Iterator((pid, part.localLeftJoin(localMsgs, needActive)(uf)))
    }.cache()

    finalPartitionsRDD.count()
    time2 = System.currentTimeMillis()
    println("Join: " + (time2 - time1))

    new GraphImpl(finalPartitionsRDD)
  }

  def computeVertexCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      // tripletFields: TripletFields,
      needActive: Boolean = false): Graph[VD, ED] = {

    partitionsRDD.cache()
    val startTime = System.currentTimeMillis()
    val preAgg = partitionsRDD.mapPartitions { partIter =>
      val (pid, graphPart) = partIter.next()
      // println("AggregateMessageVertexCentric")
      graphPart.aggregateMessagesVertexCentric(sendMsg, mergeMsg)
    }.partitionBy(new HashPartitioner(this.getNumPartitions))

    /*
    preAgg.cache()

    preAgg.count()

    val totalMessages = preAgg.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      Iterator(part.syncIterator.length + part.msgIterator.length)
    }.sum()
    */


    // println("Total Messages: " + totalMessages)

    // val msgTime = System.currentTimeMillis()

    // println("aggreMsgs: " + preAgg.count())

    val uf = (id: VertexId, data: VD, o: Option[A]) => {
      o match {
        case Some (u) => vFunc (id, data, u)
        case None => data
      }
    }

    val newPartitionsRDD = partitionsRDD.zipPartitions(preAgg) { (parts, aggMsgs) =>
      val (pid, part) = parts.next()
      // println("GenerateFinalMsgs")
      // val startTime = System.currentTimeMillis()
      val localMsgs = part.generateFinalMsgs(aggMsgs)(sendMsg, mergeMsg)
      // val midTime = System.currentTimeMillis()
      // println("GenerateMsg Time: " + (midTime - startTime))
      // localMsgs.foreach(println )
      val newPart = Iterator((pid, part.localLeftJoin(localMsgs, needActive)(uf)))
      // println("Join Time: "+ (System.currentTimeMillis() - midTime))
      newPart
      // msgs
    }.cache()

    // newPartitionsRDD.count()

    // val endTime = System.currentTimeMillis()
    // println("join Time: " + (endTime - msgTime))
    new GraphImpl(newPartitionsRDD)
  }

  override def computeEdgeCentric[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      // tripletFields: TripletFields,
      needActive: Boolean = true,
      useSrc: Boolean = true,
      useDst: Boolean = true): Graph[VD, ED] = {

    partitionsRDD.cache()

    /*
    if (needInit == true) {
      println("first time open")
      partitionsRDD.mapPartitions { partIter =>
        val (_, part) = partIter.next()
        part.generateSyncMsgsWithInit()
      }.partitionBy(this.partitioner.get)
    } else {
    */
    val syncMsgs =
      partitionsRDD.mapPartitions { partIter =>
        val (_, part) = partIter.next()
        part.generateSyncMsgs(useSrc, useDst)
      }.partitionBy(this.partitioner.get)

    // 1. sync mirrors
    // val syncMsgs =

    // println("sync messages: " + syncMsgs.count())

    // 2. get the newPart with new mirror values
    val newPartitions = partitionsRDD.zipPartitions(syncMsgs) {
      (partIter, msgIter) =>
      val (pid, part) = partIter.next ()
      //  val startTime = System.currentTimeMillis()
      val msgs = msgIter.flatMap (_._2.iterator)
      val newPart = part.syncMirrors (msgs)
      //  val endTime = System.currentTimeMillis()
      //  println("Sync cost time: " + (endTime - startTime))
      Iterator((pid, newPart))
    }.cache()

    // 3. generate messages for masters
    val preAgg = newPartitions.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      // val startTime = System.currentTimeMillis()
      val messages = part.aggregateMessagesEdgeCentric(sendMsg, mergeMsg, edgeDirection)
      // val endTime = System.currentTimeMillis()
      // println("Sync cost time: " + (endTime - startTime))
      messages
    }.partitionBy(this.partitioner.get)

    val uf = (id: VertexId, data: VD, o: Option[A]) => {
      o match {
        case Some (u) => vFunc (id, data, u)
        case None => data
      }
    }

    // 4. update new values to masters
    val newPartitionsRDD = newPartitions.zipPartitions(preAgg) { (parts, aggMsgs) =>
      val (pid, part) = parts.next()
      val localMsgs = part.generateLocalMsgs(aggMsgs)(mergeMsg)
      Iterator((pid, part.localLeftJoin(localMsgs, needActive)(uf)))
    }
    new GraphImpl(newPartitionsRDD)
  }

  // need a initFunc to decide how many vertices have value changed
  def computeEdgeCentric2[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      needActive: Boolean = true): Graph[VD, ED] = {

    partitionsRDD.cache()

    // 1. sync mirrors
    /*
    val syncMsgs = partitionsRDD.mapPartitions { partIter =>
      val (_, part) = partIter.next()

      val newBitSet = part.getDiffVertices(initFunc)
      part.generateSyncMsgsWithBitSet(newBitSet, false, true)
      // part.generateSyncMsgs(false, true)
    }.partitionBy(this.partitioner.get)
    */

    val syncMsgs = partitionsRDD.mapPartitions { partIter =>
      val (_, part) = partIter.next()
      part.generateSyncMsgs(false, true)
    }.partitionBy(this.partitioner.get)

    // println("sync messages: " + syncMsgs.count())

    // 2. get the newPart with new mirror values
    val newPartitions = partitionsRDD.zipPartitions(syncMsgs) {
      (partIter, msgIter) =>
        val (pid, part) = partIter.next ()
        val msgs = msgIter.flatMap (_._2.iterator)
        val newPart = part.syncMirrors (msgs)
        Iterator((pid, newPart))
    }.cache()

    // 3. generate messages for masters
    val preAgg = newPartitions.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      part.aggregateMessagesVertexCentric(sendMsg, mergeMsg)
    }.partitionBy(this.partitioner.get)

    val uf = (id: VertexId, data: VD, o: Option[A]) => {
      o match {
        case Some (u) => vFunc (id, data, u)
        case None => data
      }
    }

    // 4. update new values to masters
    val newPartitionsRDD = newPartitions.zipPartitions(preAgg) { (parts, aggMsgs) =>
      val (pid, part) = parts.next()
      val localMsgs = part.generateFinalMsgs(aggMsgs)(sendMsg, mergeMsg)
      Iterator((pid, part.localLeftJoin(localMsgs, needActive)(uf)))
    }
    new GraphImpl(newPartitionsRDD)
  }

  override def localOuterJoin[VD2: ClassTag](
      other: RDD[LocalFinalMessages[VD2]],
      needActive: Boolean)(updateF: (VertexId, VD, Option[VD2]) => VD): Graph[VD, ED] = {

    val newPartitionsRDD = partitionsRDD.zipPartitions(other) { (partIter, otherIter) =>
      val (pid, part) = partIter.next()
      val finalMessages = otherIter.next()
      Iterator((pid, part.localLeftJoin(finalMessages, needActive)(updateF)))
    }
    // newPartitionsRDD.foreach(p => p._2.activeSet.iterator.foreach(println))

    this.withPartitionsRDD(newPartitionsRDD)
  }

  override def withPartitionsRDD[VD2: ClassTag, ED2: ClassTag](
      partitionsRDD: RDD[(Int, GraphPartition[VD2, ED2])]): GraphImpl[VD2, ED2] = {
    new GraphImpl(partitionsRDD)
  }
}

object GraphImpl {

  def buildRoutingTable[VD: ClassTag, ED: ClassTag](
      partitions: RDD[(PartitionID, GraphPartition[VD, ED])],
      useDstMirror: Boolean = true)
  : GraphImpl[VD, ED] = {

    partitions.cache()
    val numPartitions = partitions.getNumPartitions

    val newPartitioner = partitions.partitioner
      .getOrElse(new HashPartitioner(numPartitions))
    println("Building Routing Table")

    val shippedMsgs = partitions.mapPartitions { partIter =>
      val (pid, part) = partIter.next()
      // println("smallDegreeSize largeDegreeStartPos largeDegreeEndPos"
      //   + part.smallDegreeSize + " " + part.largeDegreeStartPos + " " + part.largeDegreeEndPos
      //   + " " + part.totalVertSize)
      // part.remoteLDMirrorIterator.foreach(v => println("LDMirrors " + pid + " " + v))

      // part.remoteOutMirrorIterator.foreach(v => println("OutMirrors " + pid + " " + v))

      // part.neighborIterator.foreach(v => println("Neighbors " + pid + " " + v))

      // part.localNeighborIterator.foreach(v => println("LocalNeighbors " + pid + " " + v))
      RoutingTable.generateMessagesWithPos(pid, part, true, useDstMirror)
    }.partitionBy(newPartitioner)

    val finalPartitionsRDD = partitions.zipPartitions(shippedMsgs) { (partIter, msgIter) =>
      val (pid, part) = partIter.next()
      val routingTable = RoutingTable.fromMsgs(numPartitions, msgIter, part.global2local)
      if (useDstMirror) { // rebuild the mirror array
        val newMirrorValues = new Array[VD](part.local2global.length - part.masterSize)
        Iterator((pid, part.withMirrorValues(newMirrorValues).withRoutingTable(routingTable)))
      } else {
        val newMirrorValues = new Array[VD](part.largeDegreeMirrorEndPos - part.masterSize)
        Iterator((pid, part.withMirrorValues(newMirrorValues).withRoutingTable(routingTable)))
      }

    }

    new GraphImpl(finalPartitionsRDD)
  }

  def buildGraph[VD: ClassTag, ED: ClassTag](
      edges: RDD[(VertexId, Iterable[VertexId])],
      defaultVertexValue: VD,
      factor: Int,
      useDstMirror: Boolean = true): GraphImpl[VD, Int] = {

    val numPartitions = edges.getNumPartitions
    // remove -1s
    val newEdges = edges.map(v => (v._1, v._2.toArray)).map { v =>
      val tmpArray = v._2.filter(v => v != -1)
      if (tmpArray.length > 0) {
        (v._1, tmpArray) // having edges
      } else { // no edges
        (v._1, Array.empty[Long])
      }
    }
    newEdges.cache()
    // compute the mirror degree threshold
    val vertexSize = newEdges.count()
    val edgeSize = newEdges.flatMap(_._2).count()


    val degreeThreshold: Int = factor * (numPartitions *
       math.exp(edgeSize / (vertexSize * numPartitions))).toInt
    println("DegreeThreshold: " + degreeThreshold, edgeSize, vertexSize,
      (numPartitions *
        math.exp(edgeSize / (vertexSize * numPartitions))).toInt)

    // val degreeThreshold = 5
    // val degreeThreshold = 96

    val partitioner = new HashPartitioner(numPartitions)

    // get the edges needed to be repartitioned

    val edgeNums = newEdges.map(v => (v._1, v._2.length))
    val localEdges = newEdges.filter(_._2.length <= degreeThreshold)

    val exchangeEdges = newEdges
      .filter(_._2.length > degreeThreshold)
      .flatMap { v =>
        val srcId = v._1
        v._2.map(dstId => ((dstId, srcId))) ++ Iterator((srcId, -1L))
      }
      .partitionBy(partitioner)
    // free original edges
    // newEdges.unpersist()

    val graphPartitions = exchangeEdges
      .mapPartitionsWithIndex { (pid, edges) =>
      val mirrorEdges = edges.map(e => if (e._2 == -1L) (e._1, e._2) else (e._2, e._1))
      val createCombiner = (v: VertexId) =>
        if (v == -1L) CompactBuffer[Long]() else CompactBuffer(v)
      val mergeValue = (buf: CompactBuffer[VertexId], v: VertexId) =>
        if (v == -1L) buf else buf += v
      val mergeCombiners = (c1: CompactBuffer[VertexId], c2: CompactBuffer[VertexId]) => c1 ++= c2

      val mirrorMap = new GraphVAppendOnlyMap[
        VertexId, VertexId, CompactBuffer[VertexId]](
        createCombiner,
        mergeValue,
        mergeCombiners
      )
      mirrorMap.insertAll(mirrorEdges)
      Iterator((pid, mirrorMap.iterator.map { v =>
        val srcId = v._1
        val dstIds = v._2.toArray
        (srcId, dstIds)
      }))
    }.zipPartitions(localEdges, edgeNums) { (remoteEdgesIter, localEdges, edgeNumsIter) =>
      val (pid, remoteEdges) = remoteEdgesIter.next
      println("this pid: " + pid)
      val graphPartitionBuilder = new GraphPartitonBuilder[VD](
        degreeThreshold, numPartitions, defaultVertexValue)
      // println("localEdges: ")
      // edges.foreach(println)
      // println("remoteEdges: ")
      // remoteEdges.foreach(println)
      graphPartitionBuilder.add(localEdges, remoteEdges, edgeNumsIter)
      Iterator((pid, graphPartitionBuilder.toGraphPartition(pid)))
    }


    // val vertexSize = graphPartitionBuilders.map(_.getVertexSize).sum()
    buildRoutingTable(graphPartitions, useDstMirror)
  }

  def fromEdgeList[VD: ClassTag](
      edgeList: RDD[(VertexId, Iterable[VertexId])],
      defaultVertexAttr: VD,
      factor: Int,
      useDstMirror: Boolean = true,
      edgeStorageLevel: StorageLevel,
      vertexStorageLevel: StorageLevel,
      enableMirror: Boolean = true): GraphImpl[VD, Int] = {
    if (enableMirror) {
      buildGraph(edgeList, defaultVertexAttr, factor, useDstMirror)
    } else {
      throw new NotImplementedError()
    }
  }
}
