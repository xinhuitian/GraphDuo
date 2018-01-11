
package org.apache.spark.graphv.enhanced

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import org.apache.spark.SparkFunSuite

import org.apache.spark.graphv._
import org.apache.spark.graphx.LocalSparkContext
import org.apache.spark.util.Utils


class PregelSuite extends SparkFunSuite with LocalSparkContext {

  test ("PageRank") {
    withSpark { sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 until 101)) {
        writer.write (s"0 $i\n")
        writer.write (s"$i 0\n")
      }

      writer.close ()

      try {

        val myStartTime = System.currentTimeMillis
        val workGraph = GraphLoader.edgeListFile (sc,
          "/Users/XinhuiTian/Downloads/soc-Epinions1.txt",
          true, 8, 20, useDstMirror = false).cache ()
        // val workGraph = GraphLoader.edgeListFile(sc, tmpDir.getAbsolutePath, false, 10,
        //   useSrcMirror = true, useDstMirror = false).cache()

        println ("outDegrees")
        // workGraph.outDegrees.foreach(println)
        // workGraph.count()
        // workGraph.vertices.foreach(println)
        // workGraph.edges.foreach(println)
        // workGraph.vertices.count()

        val iniGraph: Graph[Double, Double] = workGraph
          .localOuterJoin (workGraph.localDegreeRDD (false), false) {(vid, attr, degree) => degree.getOrElse(0) }
          .mapTriplets (e => 1.0 / e.srcAttr, TripletFields.SrcWithEdge)
          .mapVertices ((id, attr) => 1.0).cache ()

        // iniGraph.vertices.foreach(println)

        // iniGraph.edges.foreach(println)

        // workGraph.localOutDegrees.foreach(_.iterator.foreach(println))

        iniGraph.vertices.count
        println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))
        /*
        iniGraph.vertices.partitionsRDD.foreach { v =>
          v.foreachEdgePartition((vid, e) => println(vid + " " + e))
        }
        */
        println ("Active masters: " + iniGraph.getActiveNums)
        // iniGraph.count()

        val newGraph = iniGraph.activateAllMasters
        newGraph.cache ()
        newGraph.vertices.count ()
        println(newGraph.edges.map(_.attr).sum())
        // println("edges")
        // workGraph.vertices.partitionsRDD.foreach { vertexPart => vertexPart.edges.foreach(println); println }
        // iniGraph.vertices.foreach(println)


        val initialMessage = 1.0

        def vertexProgram(id: VertexId, attr: Double, msgSum: Double): Double = {
          val newRank = 0.15 + (1.0 - 0.15) * msgSum
          newRank
        }

        def sendMessage(edge: GraphVEdgeTriplet[Double, Double])
        : Iterator[(VertexId, Double)] = {
          if (edge.srcAttr > 0.0001) {
            Iterator((edge.dstId, edge.srcAttr * edge.attr))
          } else {
            Iterator.empty
          }
        }

        def mergeMessage(a: Double, b: Double): Double = a + b

        val resultGraph = Pregel (newGraph, initialMessage,
          activeDirection = EdgeDirection.Out, maxIterations = 10)(
          (id, attr) => 1.0, vertexProgram, sendMessage, mergeMessage)


        println (resultGraph.vertices.map (_._2).sum ())
        // resultGraph.vertices.values.foreach(println)
        println ("total vertices: " + resultGraph.vertices.map (_._1).count)
        println ("My pregel " + (System.currentTimeMillis - myStartTime))

      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }

  test ("BFS") {
    withSpark { sc =>
      val myStartTime = System.currentTimeMillis

      val graph = GraphLoader.edgeListFile (sc,
        "/Users/XinhuiTian/Downloads/facebook-links.txt", false, 8, 20, true).cache ()
      // tmpDir.getCanonicalPath, false, 8).cache ()

      println ("Vertices: " + graph.vertices.count ())
      println ("Edges: " + graph.edges.count (), graph.edgeSize)

      var g = graph.mapVertices ((vid, attr) => Integer.MAX_VALUE, false).cache ()

      val initialMessage = Integer.MAX_VALUE

      def initialProgram(id: VertexId, attr: Int): Int = {
        if (id == 1L) 0 else attr
      }

      def vertexProgram(id: VertexId, attr: Int, msg: Int): Int = {
        if (attr == Integer.MAX_VALUE) msg
        else attr
      }

      def sendMessage(edge: GraphVEdgeTriplet[Int, _]): Iterator[(VertexId, Int)] = {
        // only visited vertices can be touched here?

        if (edge.srcAttr != Integer.MAX_VALUE && edge.dstAttr == Integer.MAX_VALUE) {
          Iterator ((edge.dstId, edge.srcAttr + 1))
        } else {
          Iterator.empty
        }
      }

      def mergeFunc(a: Int, b: Int): Int = Math.min (a, b)

      val results = Pregel (g, initialMessage, needActive = true, edgeFilter = true)(
        initialProgram, vertexProgram, sendMessage, mergeFunc)
      println (results.vertices.map (_._2).sum ())
      println ("My pregel " + (System.currentTimeMillis - myStartTime))
    }
  }

  test("SSSP") {
    withSpark { sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 to 10)) {
        writer.write (s"0 $i\n")
        // writer.write (s"$i 0\n")
      }
      for (i <- (11 to 20)) {
        writer.write (s"10 $i\n")
      }

      for (i <- (21 to 30)) {
        writer.write (s"20 $i\n")
      }

      for (i <- (31 to 40)) {
        writer.write (s"30 $i\n")
      }

      for (i <- (41 to 50)) {
        writer.write (s"40 $i\n")
      }

      writer.close ()

      val myStartTime = System.currentTimeMillis

      val graph = GraphLoader.edgeListFile (sc,
      //     "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8, true, true).cache ()
          tmpDir.getCanonicalPath, false, 10, 20, true).cache ()

      graph.vertices.count ()

      val spGraph = graph
        .mapVertices { (vid, _) => Double.PositiveInfinity }

      def initFunc(id: VertexId, attr: Double): Double = {
        if (id == 0L) 0.0 else Double.PositiveInfinity
      }

      val initialMessage = Double.PositiveInfinity

      def vertexProgram(id: VertexId, attr: Double, msg: Double): Double = math.min (attr, msg)

      def sendMessage(edge: GraphVEdgeTriplet[Double, _]): Iterator[(VertexId, Double)] = {
        val newValue = edge.srcAttr + 1.0
        println(edge.dstAttr)
        if (edge.srcAttr < Double.PositiveInfinity && newValue < edge.dstAttr) {
          Iterator ((edge.dstId, newValue))
        } else {
          Iterator.empty
        }
      }

      def mergeFunc(a: Double, b: Double): Double = math.min (a, b)

      val results = Pregel (spGraph, initialMessage,
        maxIterations = 20,
        needActive = true, edgeFilter = false)(
        initFunc, vertexProgram, sendMessage, mergeFunc).cache()

      // results.vertices.foreach(println)

      println (results.vertices.map (_._2).filter(_ != Double.PositiveInfinity).sum ())
      println ("My pregel " + (System.currentTimeMillis - myStartTime))

    }
  }


  test("DeltaPageRank") {
    withSpark{sc =>
      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 until 101)) {
        writer.write (s"0 $i\n")
      }

      for (i <- 1 until 101) {
        writer.write(s"$i 0\n")
      }


      writer.close ()

      try {

        val myStartTime = System.currentTimeMillis

        val graph = GraphLoader.edgeListFile (sc,
            "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8, 20, false).cache ()
        //   tmpDir.getCanonicalPath, false, 8).cache ()

        graph.vertices.count ()

        val resetProb = 0.15
        println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))
        val iniGraph: Graph[(Double, Double), Double] = graph
          .localOuterJoin(graph.localOutDegrees, false) { (vid, attr, degree) => degree.getOrElse(0) }
          .mapTriplets (e => 1.0 / e.srcAttr, TripletFields.SrcWithEdge)
          .mapVertices ((id, attr) => (0.0, 0.0)).cache ()

        // iniGraph.vertices.count

        // iniGraph.outDegrees.foreach(println)

        // iniGraph.vertices.foreach(println)

        val initialMessage = 1.0
        val tol = 0.0001F

        def vertexProgram(id: VertexId, attr: (Double, Double), msgSum: Double)
        : (Double, Double) = {
          val (oldPR, _) = attr
          val newPR = oldPR + (1.0 - resetProb) * msgSum
          (newPR, newPR - oldPR)
        }

        def sendMessage(edge: GraphVEdgeTriplet[(Double, Double), Double])
        : Iterator[(VertexId, Double)] = {
          if (edge.srcAttr._2 > tol) {
            Iterator ((edge.dstId, edge.srcAttr._2 * edge.attr))
          } else {
            Iterator.empty
          }
        }

        def mergeMessage(a: Double, b: Double): Double = a + b

        val resultGraph = Pregel (iniGraph, initialMessage,
          activeDirection = EdgeDirection.Out, needActive = true)(
          (id, attr) => (0.85, 0.85),
          vertexProgram,
          sendMessage,
          mergeMessage)

        println (resultGraph.vertices.values.count ())
        //resultGraph.vertices.values.foreach(println)
        println ("total vertices: " + resultGraph.vertices.map (_._2._1).sum ())
        println ("My pregel " + (System.currentTimeMillis - myStartTime))
      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }

  test ("CC") {
    withSpark { sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (0 until 101)) {
        for (j <- (1 until 10)) {
          if (j != i) {
            writer.write (s"$i $j\n")
          }
        }

        // writer.write (s"$i 0\n")
      }

      writer.close ()

      val myStartTime = System.currentTimeMillis

      val graph = GraphLoader.edgeListFile (sc,
         "/Users/XinhuiTian/Downloads/facebook-links.txt", false, 8, 20, true).cache ()
         // tmpDir.getCanonicalPath, false, 8, true, true).cache ()

      println ("Vertices: " + graph.vertices.count ())
      println ("Edges: " + graph.edges.count (), graph.edgeSize)

      var g = graph.mapVertices ((vid, attr) => -1L, false).cache ()

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

  test ("triangle count") {
    withSpark { sc =>

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (0 until 101)) {
        for (j <- (1 until 10)) {
          if (j != i) {
            writer.write (s"$i $j\n")
          }
        }

        // writer.write (s"$i 0\n")
      }

      writer.close ()

      val myStartTime = System.currentTimeMillis

      val graph = GraphLoader.edgeListFile (sc,
        "/Users/XinhuiTian/Downloads/facebook-links.txt", false, 8, 20, true).cache ()
      // tmpDir.getCanonicalPath, false, 8, true, true).cache ()

      println ("Vertices: " + graph.vertices.count ())
      println ("Edges: " + graph.edges.count (), graph.edgeSize)

      var g = graph.mapVertices ((vid, attr) => -1L, false).cache ()

      def initialProgram(id: VertexId, attr: Long): Long = id

      def vertexProgram(id: VertexId, attr: Long, msg: Long): Long = Math.min(attr, msg)

      def edgeFunc(ctx: VertexContext[VertexSet, Boolean, Int]) {
        val (smallSet, largeSet) = if (ctx.srcAttr.size < ctx.dstAttr.size) {
          (ctx.srcAttr, ctx.dstAttr)
        } else {
          (ctx.dstAttr, ctx.srcAttr)
        }
        // for each vid in the smallset, if it is also contained in largeSet
        val iter = smallSet.iterator
        var counter: Int = 0
        while (iter.hasNext) {
          val vid = iter.next()
          if (vid != ctx.srcId && vid != ctx.dstId && largeSet.contains(vid)) {
            counter += 1
          }
        }
        ctx.sendToSrc(counter)
        ctx.sendToDst(counter)
      }

      def mergeFunc(a: Long, b: Long): Long = Math.min (a, b)

      println ("My pregel " + (System.currentTimeMillis - myStartTime))
    }
  }

}