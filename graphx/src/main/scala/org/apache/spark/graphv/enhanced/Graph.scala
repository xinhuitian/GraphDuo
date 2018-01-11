
package org.apache.spark.graphv.enhanced

import scala.reflect.ClassTag

import org.apache.spark._
import org.apache.spark.graphv._
import org.apache.spark.graphx.TripletFields
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

abstract class Graph[VD: ClassTag, ED: ClassTag](
    sc: SparkContext,
    deps: Seq[Dependency[_]]) extends RDD[(VertexId, VD)](sc, deps) {

  private[graphv] def partitionsRDD: RDD[(Int, GraphPartition[VD, ED])]

  override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  override def compute(part: Partition, context: TaskContext): Iterator[(VertexId, VD)] = {
    firstParent [GraphPartition[VD, _]].iterator (part, context).next().iterator
    /*
    val p = firstParent [MyVertexPartition[VD, _]].iterator (part, context).next().iterator
    if (p.hasNext) {
      p.next ().iterator.map (_.copy ())
    } else {
      Iterator.empty
    }
    */
  }

  def vertices: RDD[(VertexId, VD)]

  def edges: RDD[Edge[ED]]

  def edgeSize: Int

  def getActiveNums: Long

  def activateAllMasters: Graph[VD, ED]

  def mapVertices[VD2: ClassTag](
      f: (VertexId, VD) => VD2,
      needActive: Boolean = false): Graph[VD2, ED]

  def mapEdges[ED2: ClassTag](map: Edge[ED] => ED2): Graph[VD, ED2]

  // used for mirror init
  def mapMirrors(f: (VertexId, VD) => VD): Graph[VD, ED]

  def localOuterJoin[VD2: ClassTag]
  (other: RDD[LocalFinalMessages[VD2]], needActive: Boolean)
    (updateF: (VertexId, VD, Option[VD2]) => VD): Graph[VD, ED]

  def syncSrcMirrors: Graph[VD, ED]

  def mapReduceTriplets[A: ClassTag](
      mapFunc: GraphVEdgeTriplet[VD, ED] => Iterator[(VertexId, A)],
      reduceFunc: (A, A) => A,
      updateFunc: (VertexId, VD, A) => VD,
      activeEdgeDirection: EdgeDirection = EdgeDirection.Out,
      // tripletFields: TripletFields,
      needActive: Boolean = false,
      edgeFilter: Boolean = false): Graph[VD, ED] = {
    def sendMsg(ctx: VertexContext[VD, ED, A]) {
      // println(ctx.srcAttr, ctx.dstAttr)
      mapFunc (ctx.toEdgeTriplet).foreach { kv =>
        val id = kv._1
        val msg = kv._2
        if (id == ctx.srcId) {
          // println("send to srcId")
          if (activeEdgeDirection == EdgeDirection.In
            || activeEdgeDirection == EdgeDirection.Both) {
            ctx.sendToSrc (msg)
          }

        } else {
          assert (id == ctx.dstId)
          // println("send to dstId")
          if (activeEdgeDirection == EdgeDirection.Out
            || activeEdgeDirection == EdgeDirection.Both) {
            // println("Send to Dst")
            ctx.sendToDst (msg)
          }
        }
      }
    }
    compute2(sendMsg, reduceFunc, updateFunc,
      activeEdgeDirection, needActive, edgeFilter)
  }

  def compute[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = false): Graph[VD, ED]

  def compute2[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      vFunc: (VertexId, VD, A) => VD,
      edgeDirection: EdgeDirection,
      // tripletFields: TripletFields,
      needActive: Boolean = false,
      edgeFilter: Boolean = false): Graph[VD, ED]

  def aggregateLocalMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true
  ): RDD[(Int, A)]

  def aggregateGlobalMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true
  ): RDD[(VertexId, A)]

  def aggregateMessages[A: ClassTag](
      sendMsg: VertexContext[VD, ED, A] => Unit,
      mergeMsg: (A, A) => A,
      edgeDirection: EdgeDirection,
      tripletFields: TripletFields,
      needActive: Boolean = true): RDD[LocalFinalMessages[A]]

  @transient lazy val outDegrees: RDD[(VertexId, Int)] =
    degreeRDD(false)

  @transient lazy val inDegrees: RDD[(VertexId, Int)] =
    degreeRDD(true)

  @transient lazy val localOutDegrees: RDD[LocalFinalMessages[Int]] =
    localDegreeRDD(false)

  @transient lazy val localInDegrees: RDD[LocalFinalMessages[Int]] =
    localDegreeRDD(true)

  def mapTriplets[ED2: ClassTag](
      map: GraphVEdgeTriplet[VD, ED] => ED2,
      tripletFields: TripletFields): Graph[VD, ED2]

  def degreeRDD(inDegree: Boolean): RDD[(VertexId, Int)] = {
    if (inDegree == true) {
      aggregateGlobalMessages(_.sendToDst(1), _ + _, EdgeDirection.Out, TripletFields.None)
    } else {
      aggregateGlobalMessages(_.sendToSrc(1), _ + _, EdgeDirection.In, TripletFields.None)
    }
  }

  def localDegreeRDD(inDegree: Boolean): RDD[LocalFinalMessages[Int]] = {
    if (inDegree == true) {
      aggregateMessages(_.sendToDst(1), _ + _, EdgeDirection.Out, TripletFields.None)
    } else {
      aggregateMessages(_.sendToSrc(1), _ + _, EdgeDirection.In, TripletFields.None)
    }
  }

  def collectNeighborIds(edgeDirection: EdgeDirection): Graph[VertexSet, ED] = {
    val nbrs = {
      if (edgeDirection == EdgeDirection.Both) {
        aggregateMessages[Array[VertexId]](
          ctx => { ctx.sendToSrc(Array(ctx.dstId)); ctx.sendToDst(Array(ctx.srcId)) },
          _ ++ _, EdgeDirection.Both, TripletFields.None)
      } else if (edgeDirection == EdgeDirection.Out) {
        aggregateMessages[Array[VertexId]](
          ctx => ctx.sendToSrc(Array(ctx.dstId)),
          _ ++ _, EdgeDirection.In, TripletFields.None)

      } else if (edgeDirection == EdgeDirection.In) {
        aggregateMessages[Array[VertexId]](
          ctx => ctx.sendToDst(Array(ctx.srcId)),
          _ ++ _, EdgeDirection.Out, TripletFields.None)
      } else {
        throw new SparkException("It doesn't make sense to collect neighbor ids without a " +
          "direction. (EdgeDirection.Both is not supported; use EdgeDirection.Either instead.)")
      }
    }

    val newGraph = this.mapVertices((v, vd) => Array.empty[VertexId])

    val graph = newGraph.localOuterJoin(nbrs, false) { (vid, vdata, nbrsOpt) =>
      nbrsOpt.getOrElse(Array.empty[VertexId])
    }.mapVertices { (vid, nbrs) =>
        val set = new VertexSet(nbrs.length)
        var i = 0
        while (i < nbrs.length) {
          if (nbrs(i) != vid) {
            set.add(nbrs(i))
          }
          i += 1
        }
        set
      }

    graph
  }

  def withPartitionsRDD[VD2: ClassTag, ED2: ClassTag](
      partitionsRDD: RDD[(Int, GraphPartition[VD2, ED2])]): Graph[VD2, ED2]
}
