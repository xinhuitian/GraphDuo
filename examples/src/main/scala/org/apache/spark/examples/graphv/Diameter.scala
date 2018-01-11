
package org.apache.spark.examples.graphv

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv._
import org.apache.spark.graphx.{TripletFields, VertexId}

/**
 * Created by XinhuiTian on 17/7/11.
 */
object Diameter {

  type Mask = ArrayBuffer[ArrayBuffer[Boolean]]
  class vertexAttr(val bitmask1: Mask = new Mask, val bitmask2: Mask = new Mask, var odd_iteration: Boolean = true)
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

  class msgGatherer(val bitmask: Mask = new Mask) extends Serializable {

    def create_bitmask(newMask: Mask): Unit = {
      newMask.foreach(bitmask.append(_))
    }

    def += (other: msgGatherer): msgGatherer = {
      new msgGatherer(bitwise_or(bitmask, other.bitmask))
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

  def create_bitmask(attr: vertexAttr, vid: VertexId): vertexAttr = {
    val hashVar = vid.toInt
    val mask1 = new ArrayBuffer[Boolean](hashVar + 2)
    for (i <- 0 until vid.toInt + 2)
      mask1.append(false)
    mask1.update(hashVar, true)
    attr.bitmask1.append(mask1)
    val mask2 = new ArrayBuffer[Boolean](hashVar + 2)
    for (i <- 0 until hashVar + 2)
      mask2.append(false)
    mask2.update(vid.toInt, true)
    attr.bitmask2.append(mask2)
    attr
  }

  def myrand(): Double = {
    Random.nextInt(19) / 20
  }

  def hash_value(): Int = {
    var ret = 0
    while (myrand() < 0.5) ret+=1
    ret
  }

  def bitwise_or(b1: Mask, b2: Mask): Mask = {
    for (i <- 0 until b1.size) {
      // fill the bi(i) bitmask
      while (b1(i).size < b2(i).size)
        b1(i).append(false)

      // update bitmask in b1(i) using b2(i)
      for (j <- 0 until b2(i).size) {
        val b = b1(i)(j)
        b1(i).update(j, b || b2(i)(j))
      }
    }
    b1
  }

  def absolute_vertex_data(bitmask: Mask): Int = {
    var count = 0
    for (i <- 0 until bitmask(0).size)
      if (bitmask(0)(i) == true)
        count += 1
    count
  }

  def approximate_pair_number(bitmask: Mask): Int = {
    var num = 0.0
    for (i <- 0 until bitmask.size) {
      var j = 0
      do { j += 1 } while (bitmask(i)(j) != false)
      if (j != bitmask(i).size)
        num += i
    }

    (math.pow(2.0, num / bitmask.size) / 0.77351).asInstanceOf[Int]
  }

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
    // reverse the src and dst
    val graph = MyGraphLoader.edgeListFile(sc, args (0), true, numEPart).cache ()

    graph.vertices.count()
    println("It took %d ms loadGraph".format(System.currentTimeMillis - myStartTime))
    val dmGraph = graph.mapVertices{ (vid, attr) =>
      val initAttr = new vertexAttr
      create_bitmask(initAttr, vid)
      // initAttr
    }

    def vertexProgram(id: VertexId, attr: vertexAttr, msg: msgGatherer): vertexAttr = {
      val newAttr = new vertexAttr(attr.bitmask1, attr.bitmask2, attr.odd_iteration)
      if (msg.bitmask.size > 0)
        bitwise_or(newAttr.bitmask1, msg.bitmask)
        // newAttr.odd_iteration = false
      newAttr
    }

    def sendMessage(edge: MyEdgeTriplet[vertexAttr, _]): Iterator[(VertexId, msgGatherer)] = {
      // send the bitmask2 of dst to src
      val gather = new msgGatherer()
      gather.create_bitmask(edge.srcAttr.bitmask1)
      Iterator((edge.dstId, gather))
    }

    // for one src, merge all the bitmask sent by dsts using bitwise-or
    def mergeFunc(a: msgGatherer, b: msgGatherer): msgGatherer = a += b

    val finalGraph = MyPregel(dmGraph, new msgGatherer) (
      vertexProgram, sendMessage, mergeFunc)
      .mapVertices((vid, attr) => absolute_vertex_data(attr.bitmask1))

    println(finalGraph.vertices.take(0))
    //resultGraph.vertices.values.foreach(println)
    println("total vertices: " + finalGraph.vertices.map(_._1).count)
    println("My pregel " + (System.currentTimeMillis - myStartTime))
  }

}
