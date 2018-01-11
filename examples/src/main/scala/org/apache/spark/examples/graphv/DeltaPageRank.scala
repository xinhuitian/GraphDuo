
package org.apache.spark.examples.graphv

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphv._
import org.apache.spark.graphx.TripletFields

/**
 * Created by XinhuiTian on 17/5/29.
 */
object DeltaPageRank {
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      System.err.println (
        "Usage: GraphLoader <file> --numEPart=<num_edge_partitions> [other options]")
      System.exit (1)
    }

    val fname = args (0)
    val optionsList = args.drop (2).map{arg =>
      arg.dropWhile (_ == '-').split ('=') match {
        case Array (opt, v) => (opt -> v)
        case _ => throw new IllegalArgumentException ("Invalid argument: " + arg)
      }
    }

    val options = mutable.Map (optionsList: _*)

    val numEPart = options.remove ("numEPart").map (_.toInt).getOrElse{
      println ("Set the number of edge partitions using --numEPart.")
      sys.exit (1)
    }

    val iterations = options.remove("numIter").map(_.toInt).getOrElse {
      println ("Set the number of iterations using --numIter.")
      sys.exit (1)
    }

    val conf = new SparkConf ()

    val sc = new SparkContext (conf.setAppName ("GraphLoad(" + fname + ")"))

    val myStartTime = System.currentTimeMillis
    val graph = MyGraphLoader.edgeListFile(sc, args (0), false, numEPart).cache ()

    graph.vertices.count()

    val resetProb = 0.15
    println("It took %d ms loadGraph".format(System.currentTimeMillis - myStartTime))
    val iniGraph: MyGraph[(Double, Double), Double] = graph
      .outerJoinLocalVertices(graph.outDegrees, false) { (vid, attr, degree) => degree.getOrElse(0) }
      .mapTriplets( e => 1.0 / e.srcAttr, TripletFields.Src)
      .mapVertices( (id, attr) => (0.0, 0.0) ).cache()

    // iniGraph.vertices.count

    // workGraph.outDegrees.foreach(println)

    // iniGraph.vertices.foreach(println)

    val initialMessage = 1.0
    val tol = 0.001F
    def vertexProgram(id: VertexId, attr: (Double, Double), msgSum: Double): (Double, Double) = {
      val (oldPR, lastDelta) = attr
      val newPR = oldPR + (1.0 - resetProb) * msgSum
      if (newPR - oldPR < tol) { // do not change the value u
        (oldPR, lastDelta)
      } else {
        (newPR, newPR - oldPR)
      }
    }

    def sendMessage(edge: MyEdgeTriplet[(Double, Double), Double]) = {
      if (edge.srcAttr._2 > tol) {
        Iterator((edge.dstId, edge.srcAttr._2 * edge.attr))
      } else {
        Iterator.empty
      }
    }

    def mergeMessage(a: Double, b: Double): Double = a + b

    val resultGraph = MyPregel(iniGraph, initialMessage,
      activeDirection = EdgeDirection.Out, maxIterations = iterations,
      needActive = true) (vertexProgram, sendMessage, mergeMessage)

    resultGraph.cache()
    println(resultGraph.vertices.values.count())
    //resultGraph.vertices.values.foreach(println)
    println("total vertices: " + resultGraph.vertices.map(_._1).count)
    println("My pregel " + (System.currentTimeMillis - myStartTime))
  }
}
