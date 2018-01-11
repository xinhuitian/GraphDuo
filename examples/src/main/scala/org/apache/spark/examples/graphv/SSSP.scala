
package org.apache.spark.examples.graphv

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphv._

/**
 * Created by XinhuiTian on 17/5/19.
 */
object SSSP {
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
    val graph = MyGraphLoader.edgeListFile (sc, args (0), false, numEPart).cache()

    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))

    // val landmark = graph.vertices.map(_._1).take(0)(0)

    // val landmark: Long = 61

    val spGraph = graph.mapVertices { (vid, _) =>
      if (vid == 61L) 0.0 else Double.PositiveInfinity
    }

    val initialMessage = Double.PositiveInfinity

    def vertexProgram(id: VertexId, attr: Double, msg: Double): Double = math.min(attr, msg)

    def sendMessage(edge: MyEdgeTriplet[Double, _]): Iterator[(VertexId, Double)] = {
      if (edge.srcAttr < Double.PositiveInfinity) {
        Iterator((edge.dstId, edge.srcAttr + 1.0))
      } else {
        Iterator.empty
      }
    }

    def mergeFunc(a: Double, b: Double): Double = math.min(a, b)

    MyPregel(spGraph, initialMessage,
      maxIterations = iterations,
      needActive = true)(vertexProgram, sendMessage, mergeFunc)
  }

}
