
package org.apache.spark.graphv

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import org.apache.spark.SparkFunSuite

import org.apache.spark.graphx.LocalSparkContext
import org.apache.spark.util.Utils

/**
 * Created by XinhuiTian on 17/5/18.
 */
class GraphLoadSuite extends SparkFunSuite with LocalSparkContext {

  test ("GraphLoader.edgeListFile"){
    withSpark{sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 until 101)) {
        writer.write (s"$i 0\n")
      }
      /*
      for (i <- (1 until 101)) {
        writer.write(s"0 $i\n")
      }
      */

      writer.close ()

      try {
        val startTime = System.currentTimeMillis ()
        // val graph = GraphLoader.edgeListFile(sc,
        //  "/Users/XinhuiTian/Downloads/roadNet-CA.txt", false, 10,
        //   edgePartitioner = "EdgePartition2D").cache()

        val graph = MyGraphLoader.edgeListFile(sc, tmpDir.getAbsolutePath, false, 10)
        // val graph = MyGraphLoader.edgeListFile (sc, "/Users/XinhuiTian/Downloads/roadNet-CA.txt", false, 500)
        graph.vertices.count ()
        // graph.vertices.foreach(println)
        // graph.vertices.partitionsRDD.foreach { iter => iter.vertexIds.iterator.foreach(println); println}

        // println("attr size")
        // graph.vertices.partitionsRDD.foreach { iter => println(iter.attrs.size) }

        // println("local2global")
        // graph.vertices.partitionsRDD.foreach { iter => iter.local2global.foreach(println); println }
        // println("Mirrors")
        // graph.edges.partitionsRDD.foreach{ part => part._2.getMirrors.foreach(println); println }

        //graph.edges.partitionsRDD.foreach{ part => part._2.changeVertexAttrs(i => i + 1).foreach(println); println}
        // graph.edges.partitionsRDD
        //graph.subGraphs.collect()
        //graph.subGraphs.partitionsRDD.foreach { iter => print(iter._1);
        //  iter._2.masterIterator.foreach(print); println }
        //graph.subGraphs.partitionsRDD.foreach { iter => print(iter._1);
        //  iter._2.mirrorIterator.foreach(print); println }
        //graph.vertices.partitionsRDD.foreach { iter => iter._2.iterator.foreach(println); println }
        // graph.vertices.partitionsRDD.foreach { iter => iter._2.shipVertexAttributes(10).foreach{ vb => print(vb._1); vb._2.iterator.foreach(print)}; println}

        graph.toGraphX.vertices.count()

      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }
}
