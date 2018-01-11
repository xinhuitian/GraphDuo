
package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.internal.Logging
import org.apache.spark.util.collection.BitSet

/**
 * Created by XinhuiTian on 17/5/21.
 */
class MyShippableLocalVertexPartition[VD: ClassTag](
    val global2local: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    val values: Array[VD],
    val mask: BitSet) extends Serializable with Logging {

  // val capacity: Int = index.size

  def size: Int = values.length


  def iterator: Iterator[(Int, VD)] =
  mask.iterator.map (ind => (ind, values (ind)))

  /*
  def iterator: Iterator[(Int, VD)] =
    mask.iterator.map (ind => (ind, values (ind)))
  */

  // iter1: remote msgs, iter2: local msgs
  def aggregateUsingIndex[VD2: ClassTag](
    iter: Iterator[(VertexId, VD2)],
    reduceFunc: (VD2, VD2) => VD2): MyShippableLocalVertexPartition[VD2] = {
    val newMask = new BitSet (size)
    val newValues = new Array[VD2](size)
    // println("aggregateIndex size: " + size)
    iter.foreach { product =>
      val vid = product._1
      val vdata = product._2
      val pos = global2local.getOrElse (vid, -1)
      if (pos >= 0) {
        if (newMask.get (pos)) {
          newValues (pos) = reduceFunc (newValues (pos), vdata)
        } else { // otherwise just store the new value
          newMask.set (pos)
          newValues (pos) = vdata
        }
    //        println("debug")
      }
    }

    // println("In aggregateUsingIndex: ")
    // iter2.foreach(msg => print(msg + " "))
    // println
/*
    iter2.foreach { product =>
      val vid = product._1
      val vdata = product._2
      val pos = vid
      if (pos >= 0) {
        if (newMask.get (pos)) {
          newValues (pos) = reduceFunc (newValues (pos), vdata)
        } else { // otherwise just store the new value
          newMask.set (pos)
          newValues (pos) = vdata
          println("localMsg: " + pos, vdata)
          // println(local2global(pos), vdata)
        }
        //        println("debug")
      }
    }
    */
    new MyShippableLocalVertexPartition(global2local, newValues, newMask)
  }
}
