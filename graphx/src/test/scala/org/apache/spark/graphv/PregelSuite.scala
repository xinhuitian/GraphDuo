
package org.apache.spark.graphv

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

import org.apache.spark.SparkFunSuite

import org.apache.spark.graphx.{LocalSparkContext, TripletFields}
import org.apache.spark.util.Utils

/**
 * Created by XinhuiTian on 17/5/18.
 */


class PregelSuite extends SparkFunSuite with LocalSparkContext {
  @transient type Mask = ArrayBuffer[ArrayBuffer[Boolean]]

  test("MyPregel iteration") {
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
      val workGraph = MyGraphLoader.edgeListFile(sc, "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8).cache()
      // val workGraph = MyGraphLoader.edgeListFile(sc, tmpDir.getAbsolutePath, false, 10).cache()

      println("outDegrees")
      // workGraph.outDegrees.foreach(println)
            workGraph.vertices.count()
       // workGraph.vertices.foreach(println)
      // workGraph.vertices.count()
      println("It took %d ms loadGraph".format(System.currentTimeMillis - myStartTime))
      val iniGraph: MyGraph[Double, Double] = workGraph
        .outerJoinLocalVertices(workGraph.outDegrees, false) { (vid, attr, degree) => degree.getOrElse(0) }
        .mapTriplets( e => 1.0 / e.srcAttr, TripletFields.Src)
        .mapVertices( (id, attr) => 1.0 ).cache()

      iniGraph.vertices.count
      /*
      iniGraph.vertices.partitionsRDD.foreach { v =>
        v.foreachEdgePartition((vid, e) => println(vid + " " + e))
      }
      */
      println(iniGraph.vertices.partitionsRDD.mapPartitions(p => p.flatMap(_.edgeAttrs)).sum())
      // println("edges")
      // workGraph.vertices.partitionsRDD.foreach { vertexPart => vertexPart.edges.foreach(println); println }
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

      val resultGraph = MyPregel(iniGraph, initialMessage,
        activeDirection = EdgeDirection.Out, maxIterations = 10) (vertexProgram, sendMessage, mergeMessage)
      println(resultGraph.vertices.values.sum())
      // resultGraph.vertices.values.foreach(println)
      println("total vertices: " + resultGraph.vertices.map(_._1).count)
      println("My pregel " + (System.currentTimeMillis - myStartTime))
      } finally {
        Utils.deleteRecursively (tmpDir)
      }
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

        val graph = MyGraphLoader.edgeListFile (sc,
           "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8).cache ()
           //tmpDir.getCanonicalPath, false, 8).cache ()

        graph.vertices.count ()

        val resetProb = 0.15
        println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))
        val iniGraph: MyGraph[(Double, Double), Double] = graph
          .outerJoinLocalVertices (graph.outDegrees, false){(vid, attr, degree) => degree.getOrElse (0)}
          .mapTriplets (e => 1.0 / e.srcAttr, TripletFields.Src)
          .mapVertices ((id, attr) => (0.0, 0.0)).cache ()

        // iniGraph.vertices.count

        // workGraph.outDegrees.foreach(println)

        // iniGraph.vertices.foreach(println)

        val initialMessage = 1.0
        val tol = 0.0001

        def vertexProgram(id: VertexId, attr: (Double, Double), msgSum: Double): (Double, Double) = {
          val (oldPR, lastDelta) = attr
          val newPR = oldPR + (1.0 - resetProb) * msgSum
          (newPR, newPR - oldPR)
        }

        def sendMessage(edge: MyEdgeTriplet[(Double, Double), Double]) = {
          if (edge.srcAttr._2 > tol) {
            Iterator ((edge.dstId, edge.srcAttr._2 * edge.attr))
          } else {
            Iterator.empty
          }
        }

        def mergeMessage(a: Double, b: Double): Double = a + b

        val resultGraph = MyPregel (iniGraph, initialMessage, activeDirection = EdgeDirection.Out, needActive = true)(vertexProgram, sendMessage, mergeMessage)


        resultGraph.cache ()
        println (resultGraph.vertices.values.count ())
        //resultGraph.vertices.values.foreach(println)
        println ("total vertices: " + resultGraph.vertices.map (_._2._1).sum ())
        println ("My pregel " + (System.currentTimeMillis - myStartTime))
      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }

  test("SSSP"){
    withSpark{sc =>
      val myStartTime = System.currentTimeMillis

      val graph = MyGraphLoader.edgeListFile (sc,
        "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8).cache ()
      // tmpDir.getCanonicalPath, false, 8).cache ()

      graph.vertices.count ()

      val spGraph = graph.mapVertices{(vid, _) =>
        if (vid == 61L) 0.0 else Double.PositiveInfinity
      }

      val initialMessage = Double.PositiveInfinity

      def vertexProgram(id: VertexId, attr: Double, msg: Double): Double = math.min (attr, msg)

      def sendMessage(edge: MyEdgeTriplet[Double, _]): Iterator[(VertexId, Double)] = {
        if (edge.srcAttr < Double.PositiveInfinity) {
          Iterator ((edge.dstId, edge.srcAttr + 1.0))
        } else {
          Iterator.empty
        }
      }

      def mergeFunc(a: Double, b: Double): Double = math.min (a, b)

      val results = MyPregel (spGraph, initialMessage,
        maxIterations = 20,
        needActive = true)(vertexProgram, sendMessage, mergeFunc)

      println (results.vertices.map (_._2).filter(_ != Double.PositiveInfinity).sum ())
      println ("My pregel " + (System.currentTimeMillis - myStartTime))

    }
  }

  test("BFS") {
    withSpark { sc =>
      val myStartTime = System.currentTimeMillis

      val graph = MyGraphLoader.edgeListFile (sc,
        "/Users/XinhuiTian/Downloads/facebook-links.txt", false, 8).cache ()
      // tmpDir.getCanonicalPath, false, 8).cache ()

      println("Vertices: " + graph.vertices.count)
      // println("Edges: " + graph.edges.count)

      var g = graph.mapVertices { (vid, attr) =>
        if (vid == 1L) 0 else Integer.MAX_VALUE
      }.cache()

      val initialMessage = Integer.MAX_VALUE

      def vertexProgram(id: VertexId, attr: Int, msg: Int): Int = {
        if (attr == Integer.MAX_VALUE) msg
        else attr
      }

      def sendMessage(edge: MyEdgeTriplet[Int, _]): Iterator[(VertexId, Int)] = {
        // only visited vertices can be touched here?

        if (edge.srcAttr < Int.MaxValue) {
          Iterator ((edge.dstId, edge.srcAttr + 1))
        } else {
          Iterator.empty
        }
      }

      def mergeFunc(a: Int, b: Int): Int = Math.min(a, b)

      val results = MyPregel(g, initialMessage, needActive = true)(
        vertexProgram, sendMessage, mergeFunc)
      println(results.vertices.map(_._2).sum())
      println ("My pregel " + (System.currentTimeMillis - myStartTime))
    }
  }






  object VertexAttr {

  }
  test("diameter"){
    withSpark{sc =>

      class VertexAttr(val bitmask1: Mask = new Mask, val bitmask2: Mask = new Mask, var odd_iteration: Boolean = true)
        extends Serializable {
        override def toString(): String = {
          var bit1 = new String
          for (i <- 0 until bitmask1(0).size)
            if (bitmask1(0)(i) == true)
              bit1 += "1"
            else bit1 += "0"

          var bit2 = new String
          for (i <- 0 until bitmask2(0).size)
            if (bitmask2(0)(i) == true)
              bit2 += "1"
            else bit2 += "0"
          bitmask1(0).size + " " + bit1 + " " + bit2
        }
      }

      class MsgGatherer(val bitmask: Mask = new Mask) extends Serializable {

        def create_bitmask(newMask: Mask): Unit = {
          newMask.foreach(mask => bitmask.append(mask))
        }

        def += (other: MsgGatherer): MsgGatherer = {
          new MsgGatherer(bitwise_or(bitmask, other.bitmask))
        }

        private def bitwise_or = (b1: Mask, b2: Mask) => {
          for (i <- 0 until b1.size) {
            // fill the bi(i) bitmask
            while (b1 (i).size < b2 (i).size)
              b1 (i).append (false)

            // update bitmask in b1(i) using b2(i)
            for (j <- 0 until b2 (i).size) {
              val b = b1 (i)(j)
              b1 (i).update (j, b || b2 (i)(j))
            }
          }
          b1
        }

        override def toString(): String = {
          var bit1 = new String
          for (i <- 0 until bitmask(0).size)
            if (bitmask(0)(i) == true)
              bit1 += "1"
            else bit1 += "0"

          bitmask(0).size + " " + bit1
        }
      }

      def create_bitmask = (attr: VertexAttr, vid: VertexId) => {
        val hashVar = vid.toInt
        val mask1 = new ArrayBuffer[Boolean](hashVar + 2)
        for (i <- 0 until vid.toInt + 2)
          mask1.append (false)
        mask1.update (hashVar, true)
        attr.bitmask1.append (mask1)
        val mask2 = new ArrayBuffer[Boolean](hashVar + 2)
        for (i <- 0 until hashVar + 2)
          mask2.append (false)
        mask2.update (vid.toInt, true)
        attr.bitmask2.append (mask2)
        attr
      }

      def myrand(): Double = {
        Random.nextInt (19) / 20
      }

      def hash_value(): Int = {
        var ret = 0
        while (myrand () < 0.5) ret += 1
        ret
      }

      def bitwise_or = (b1: Mask, b2: Mask) => {
        for (i <- 0 until b1.size) {
          // fill the bi(i) bitmask
          while (b1 (i).size < b2 (i).size)
            b1 (i).append (false)

          // update bitmask in b1(i) using b2(i)
          for (j <- 0 until b2 (i).size) {
            val b = b1 (i)(j)
            b1 (i).update (j, b || b2 (i)(j))
          }
        }
        b1
      }

      def absolute_vertex_data(bitmask: Mask): Int = {
        var count = 0
        for (i <- 0 until bitmask (0).size)
          if (bitmask (0)(i) == true)
            count += 1
        count
      }

      def approximate_pair_number(bitmask: Mask): Int = {
        var num = 0.0
        for (i <- 0 until bitmask.size) {
          var j = 0
          do {
            j += 1
          } while (bitmask (i)(j) != false)
          if (j != bitmask (i).size)
            num += i
        }

        (math.pow (2.0, num / bitmask.size) / 0.77351).asInstanceOf [Int]
      }

      val tmpDir = Utils.createTempDir ()
      val graphFile = new File (tmpDir.getAbsolutePath, "graph.txt")
      // println(tmpDir.getAbsolutePath)
      val writer = new OutputStreamWriter (new FileOutputStream (graphFile), StandardCharsets.UTF_8)
      for (i <- (1 until 101)) {
        writer.write (s"$i 0\n")
      }

      writer.close ()

      try {
        val myStartTime = System.currentTimeMillis

        val graph = MyGraphLoader.edgeListFile (sc,
          //   "/Users/XinhuiTian/Downloads/soc-Epinions1.txt", false, 8).cache ()
          tmpDir.getCanonicalPath, false, 8).cache ()

        graph.vertices.count ()
        println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))
        val dmGraph = graph.mapVertices{(vid, attr) =>
          val newAttr = new VertexAttr()

          create_bitmask(newAttr, vid)
          newAttr
        }
        dmGraph.vertices.count ()



        println ("After first map")

        def vertexProgram = (id: VertexId, attr: VertexAttr, msg: MsgGatherer) => {
          // if (msg.size > 0)
          //   bitwise_or (attr, msg)
          // newAttr.odd_iteration = false
          val newAttr = new VertexAttr(attr.bitmask1, attr.bitmask2, attr.odd_iteration)
          if (msg.bitmask.size > 0)
            bitwise_or(newAttr.bitmask1, msg.bitmask)
          newAttr
        }

        def sendMessage = (edge: MyEdgeTriplet[VertexAttr, _]) => {
          // send the bitmask2 of dst to src
          // val gather = new msgGatherer ()
          // gather.create_bitmask (edge.srcAttr.bitmask1)
          val gather = new MsgGatherer()
          gather.create_bitmask(edge.srcAttr.bitmask1)
          Iterator((edge.dstId, gather))
        }

        // for one src, merge all the bitmask sent by dsts using bitwise-or
        def mergeFunc(a: MsgGatherer, b: MsgGatherer): MsgGatherer = a += b

        @transient
        val initAttr: MsgGatherer = new MsgGatherer()

        val finalGraph = MyPregel (dmGraph, initAttr, maxIterations = 10)(
          vertexProgram, sendMessage, mergeFunc)
          .mapVertices ((vid, attr) => absolute_vertex_data (attr.bitmask1))

        finalGraph.vertices.foreach(println)
        //resultGraph.vertices.values.foreach(println)
        println ("total vertices: " + finalGraph.vertices.map (_._1).count)
        println ("My pregel " + (System.currentTimeMillis - myStartTime))
      } finally {
        Utils.deleteRecursively (tmpDir)
      }
    }
  }
}
