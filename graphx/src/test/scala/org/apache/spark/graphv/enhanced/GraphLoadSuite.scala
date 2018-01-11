
package org.apache.spark.graphv.enhanced

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import org.apache.spark.SparkFunSuite

import org.apache.spark.graphx.LocalSparkContext
import org.apache.spark.util.Utils

class GraphLoadSuite extends SparkFunSuite with LocalSparkContext {

  test ("GraphLoader.edgeListFile") {
    withSpark{sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 until 101)) {
        writer.write (s"0 $i\n")
      }

      writer.close ()

      try {
        val startTime = System.currentTimeMillis ()
        // val graph = GraphLoader.edgeListFile(sc,
        //  "/Users/XinhuiTian/Downloads/roadNet-CA.txt", false, 10,
        //   edgePartitioner = "EdgePartition2D").cache()

        val graph = GraphLoader.edgeListFile(sc, tmpDir.getAbsolutePath, false, 10).cache()
        // val graph = MyGraphLoader.edgeListFile (sc, "/Users/XinhuiTian/Downloads/roadNet-CA.txt", false, 500)


        graph.partitionsRDD.foreachPartition{partIter =>
          val (pid, part) = partIter.next()
          println(pid + " " + part.indexStartPos + " " + part.smallDegreeEndPos + " "
            + part.largeDegreeMirrorEndPos + " " + part.largeDegreeMasterEndPos)
          println
        }

        graph.edges.foreach(println)
        graph.localOutDegrees.foreach(p => p.iterator.foreach(println))
        println
        // graph.inDegrees.foreach(println)
      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }

}
