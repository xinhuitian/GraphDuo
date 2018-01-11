package org.apache.spark.examples.graphv

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv._
import org.apache.spark.graphx.TripletFields

/**
 * Created by XinhuiTian on 17/5/19.
 */
object PageRank {
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
    println("It took %d ms loadGraph".format(System.currentTimeMillis - myStartTime))
    val iniGraph: MyGraph[Double, Double] = graph
      .outerJoinLocalVertices(graph.outDegrees, false) { (vid, attr, degree) => degree.getOrElse(0) }
      .mapTriplets( e => 1.0 / e.srcAttr, TripletFields.Src)
      .mapVertices( (id, attr) => 1.0 ).cache()

    // iniGraph.vertices.count

    // workGraph.outDegrees.foreach(println)

    // iniGraph.vertices.foreach(println)

    val initialMessage = 1.0
    def vertexProgram(id: VertexId, attr: Double, msgSum: Double): Double = {
      val newRank = 0.15 + (1.0 - 0.15) * msgSum
      newRank
    }
    def sendMessage(edge: MyEdgeTriplet[Double, Double]) = {
      Iterator((edge.dstId, edge.srcAttr * edge.attr))
    }

    def mergeMessage(a: Double, b: Double): Double = a + b

    val resultGraph = MyPregel(iniGraph, initialMessage, activeDirection = EdgeDirection.Out, maxIterations = iterations) (vertexProgram, sendMessage, mergeMessage)
    resultGraph.cache()
    println(resultGraph.vertices.values.sum())
    //resultGraph.vertices.values.foreach(println)
    println("total vertices: " + resultGraph.vertices.map(_._1).count)
    println("My pregel " + (System.currentTimeMillis - myStartTime))
  }
}
