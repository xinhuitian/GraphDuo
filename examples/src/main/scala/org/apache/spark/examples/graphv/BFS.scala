package org.apache.spark.examples.graphv

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv.{MyEdgeTriplet, MyGraphLoader, MyPregel, VertexId}

/**
 * Created by XinhuiTian on 17/5/19.
 */
object BFS {
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

    val conf = new SparkConf ()

    val sc = new SparkContext (conf.setAppName ("GraphLoad(" + fname + ")"))

    val myStartTime = System.currentTimeMillis
    val graph = MyGraphLoader.edgeListFile (sc, args (0), false, numEPart).cache ()

    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))

    val landmark = graph.vertices.map(_._1).take(0)

    val spGraph = graph.mapVertices { (vid, attr) =>
      if (vid == landmark) 0 else Integer.MAX_VALUE
    }

    val initialMessage = Integer.MAX_VALUE

    def vertexProgram(id: VertexId, attr: Int, msg: Int): Int = Math.min(attr, msg)

    def sendMessage(edge: MyEdgeTriplet[Int, _]): Iterator[(VertexId, Int)] = {
      val newAttr = edge.srcAttr + 1
      Iterator((edge.dstId, newAttr))
    }

    def mergeFunc(a: Int, b: Int): Int = Math.min(a, b)
    MyPregel(spGraph, initialMessage)(vertexProgram, sendMessage, mergeFunc)
  }
}
