
package org.apache.spark.examples.graphv

import scala.collection.mutable

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv.MyGraphLoader
import org.apache.spark.graphx.{GraphLoader, PartitionStrategy}

object ToGraphX {
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

    val iterations = options.remove ("numIter").map (_.toInt).getOrElse{
      println ("Set the number of iterations using --numIter.")
      sys.exit (1)
    }

    val conf = new SparkConf ()

    val sc = new SparkContext (conf.setAppName ("GraphLoad(" + fname + ")"))

    val myStartTime = System.currentTimeMillis
    // val graph = MyGraphLoader.edgeListFile (sc, args (0), false, numEPart).cache ()
    val graph = GraphLoader.edgeListFile(sc, args(0), false, numEPart).cache()

    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))

    var startTime = System.currentTimeMillis()

    val partitionedGraph = graph.partitionBy(PartitionStrategy.EdgePartition2D, numEPart)

    partitionedGraph.vertices.count()
    var endTime = System.currentTimeMillis()
    println("origin partition Took time: " + (endTime - startTime))

    /*
    startTime = System.currentTimeMillis()
    val locAwarePartitionedGraph = graph
      .locAwarePartitionBy(PartitionStrategy.EdgePartition2D, numEPart)

    locAwarePartitionedGraph.vertices.count()
    endTime = System.currentTimeMillis()

    println("local aware partition: " + (endTime - startTime))

*/
    /*
    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))
    */
    // val landmark = graph.vertices.map(_._1).take(0)(0)

    // val landmark: Long = 61

    /*
    var startTime = System.currentTimeMillis()
    val newGraph = graph.toGraphX.partitionBy(PartitionStrategy.EdgePartition2D, numEPart)

    newGraph.cache()


    newGraph.vertices.count()

    var endTime = System.currentTimeMillis()
    println("GraphV2GraphX with PartitionTook time: " + (endTime - startTime))

*/
    /*
    val newGraph = graph.toGraphX.cache()

    newGraph.vertices.count()


    var startTime = System.currentTimeMillis()

    val graphv = newGraph.toGraphV().cache()

      graphv.vertices.count()

    var endTime = System.currentTimeMillis()
    println("GraphX2GraphV Took time: " + (endTime - startTime))

    startTime = System.currentTimeMillis()

    graphv.toGraphX.vertices.count()

    endTime = System.currentTimeMillis()
    println("GraphV2GraphX Took time: " + (endTime - startTime))

    */
  }

}
