package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.internal.Logging
import org.apache.spark.util.collection.BitSet

/**
 * Created by sunny on 5/5/16.
 */
class MyShippableVertexPartition[VD](
    val global2local: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    val local2global: Array[VertexId],
    val values: Array[VD],
    val mask: BitSet) extends Serializable with Logging {

  // val capacity: Int = index.size

  def size: Int = values.length


  def iterator: Iterator[(VertexId, VD)] =
    mask.iterator.map (ind => (local2global (ind), values (ind)))

  /*
  def iterator: Iterator[(Int, VD)] =
    mask.iterator.map (ind => (ind, values (ind)))
  */

  def aggregateUsingIndex[VD2: ClassTag](
      iter: Iterator[(VertexId, VD2)],
      reduceFunc: (VD2, VD2) => VD2): MyShippableVertexPartition[VD2] = {
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
    new MyShippableVertexPartition(global2local, local2global, newValues, newMask)
  }
}
