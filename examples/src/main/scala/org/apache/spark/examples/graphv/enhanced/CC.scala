
package org.apache.spark.examples.graphv.enhanced

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv.{EdgeDirection, VertexId}
import org.apache.spark.graphv.enhanced.{GraphLoader, GraphVEdgeTriplet, Pregel}

object CC {
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

    val factor = options.remove("numFactor").map(_.toInt).getOrElse {
      println ("Set the number of iterations using --numIter.")
      sys.exit (1)
    }

    val conf = new SparkConf ()

    val sc = new SparkContext (conf.setAppName ("GraphLoad(" + fname + ")"))

    val myStartTime = System.currentTimeMillis
    val graph = GraphLoader.edgeListFile (sc, args (0), false, numEPart, factor).cache()

    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))

    // val landmark = graph.vertices.map(_._1).take(0)(0)

    // val landmark: Long = 61

    val g = graph.mapVertices ((vid, attr) => -1L, false).cache ()

    def initialProgram(id: VertexId, attr: Long): Long = id

    def vertexProgram(id: VertexId, attr: Long, msg: Long): Long = Math.min(attr, msg)

    def sendMessage(edge: GraphVEdgeTriplet[Long, _]): Iterator[(VertexId, Long)] = {
      // only visited vertices can be touched here?

      // println("edges: " + edge + (edge.srcAttr, edge.dstAttr))
      if (edge.srcAttr < edge.dstAttr) {
        // println("send to dstId")
        Iterator ((edge.dstId, edge.srcAttr))
      } else if (edge.srcAttr > edge.dstAttr) {
        // println("send to srcId")
        Iterator ((edge.srcId, edge.dstAttr))
      } else {
        Iterator.empty
      }
    }

    def mergeFunc(a: Long, b: Long): Long = Math.min (a, b)

    val results = Pregel (g, Long.MaxValue, needActive = true,
      activeDirection = EdgeDirection.Both)(
      initialProgram, vertexProgram, sendMessage, mergeFunc)
    println (results.vertices.map (_._2).sum ())
    println ("My pregel " + (System.currentTimeMillis - myStartTime))
  }
}
