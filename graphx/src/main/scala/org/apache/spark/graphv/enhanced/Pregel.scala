
package org.apache.spark.graphv.enhanced

import scala.reflect.ClassTag

import org.apache.spark.graphv.{EdgeDirection, VertexId}
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD

object Pregel extends Logging {

  def apply[VD: ClassTag, ED: ClassTag, A: ClassTag](
      graph: Graph[VD, ED],
      initialMsg: A,
      maxIterations: Int = Int.MaxValue,
      activeDirection: EdgeDirection = EdgeDirection.Out,
      // triplets: TripletFields = TripletFields.BothSidesWithEdge,
      needActive: Boolean = false,
      edgeFilter: Boolean = false)
    (initialFunc: (VertexId, VD) => VD,
        vFunc: (VertexId, VD, A) => VD,
      sendMsg: GraphVEdgeTriplet[VD, ED] => Iterator[(VertexId, A)],
      mergeMsg: (A, A) => A): Graph[VD, ED] = {

    // init values and the active states
    var g = graph.mapVertices((vid, v) => initialFunc(vid, v), needActive)
      .mapMirrors((vid, v) => initialFunc(vid, v)).cache()

    var edgeNum: Long = 0
    if (edgeFilter == true) {
      edgeNum = g.partitionsRDD.mapPartitions(_.map(_._2.edgeSize)).sum().toLong
    }
    // val vertNum = g.vertices.count()

    var edgeCentric: Boolean = false
    var needInit: Boolean = false
    var firstInit: Boolean = true

    // g.vertices.foreach(println)

    /*
    g.partitionsRDD.foreach { part =>
       part._2.mirrorAttrs.foreach(println)
    }
    */

    var activeNums = g.getActiveEdgeNums
    println("activeEdgeNums: " + activeNums)

    var prevG: Graph[VD, ED] = null
    var i = 0
    while (activeNums > 0 && (needActive || i < maxIterations)) {
      val jobStartTime = System.currentTimeMillis
      println("activeNums: " + activeNums)
      prevG = g

      if (edgeFilter == true) {
        if (activeNums > edgeNum / 20) {
          println("Open edgeCentric")
          edgeCentric = true
        } else {
          println("Close edgeCentric")
          edgeCentric = false
        }
      }

      g = prevG.mapReduceTriplets(sendMsg, mergeMsg, vFunc,
        activeDirection, needActive, edgeCentric, needInit)
        .cache()

      // g.vertices.foreach(println)
      activeNums = g.getActiveEdgeNums
      println("totalMsgs: " + g.getTotalMsgs + " noDupMsgs: " + g.getTotalNoDupMsgs)
      // println(g.vertices.map(_._2).asInstanceOf[RDD[Double]].sum())
      println ("iteration  " + i + "  It took %d ms count message"
        .format (System.currentTimeMillis - jobStartTime))

      prevG.unpersist(blocking = false)
      i += 1
    }
    g
  }
}
