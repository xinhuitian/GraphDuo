package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap

/**
 * Created by sunny on 4/25/16.
 */
class MyEdgePartition[@specialized (Char, Int, Boolean, Byte, Long, Float, Double) ED: ClassTag]
(
    localSrcIds: Array[Int],
    localDstIds: Array[Int],
    data: Array[ED],
    index: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    global2local: GraphXPrimitiveKeyOpenHashMap[VertexId, Int],
    local2global: Array[VertexId])
  extends Serializable {
  /** No-arg constructor for serialization. */
  //private def this() = this(null, null, null, null, null, null, null, null)


  @inline private def srcIds(pos: Int): VertexId = local2global (localSrcIds (pos))

  @inline private def dstIds(pos: Int): VertexId = local2global (localDstIds (pos))

  @inline private def attrs(pos: Int): ED = data (pos)

  /** Return a new `EdgePartition` with the specified edge data. */
  def withData[ED2: ClassTag](data: Array[ED2]): MyEdgePartition[ED2] = {
    new MyEdgePartition (
      localSrcIds, localDstIds, data, index, global2local, local2global)
  }

  def map[ED2: ClassTag](f: Edge[ED] => ED2): MyEdgePartition[ED2] = {
    val newData = new Array[ED2](data.length)
    val edge = new Edge[ED]()
    val size = data.length
    var i = 0
    while (i < size) {
      edge.srcId = srcIds (i)
      edge.dstId = dstIds (i)
      edge.attr = data (i)
      newData (i) = f (edge)
      i += 1
    }
    this.withData (newData)
  }

  def map[ED2: ClassTag](iter: Iterator[ED2]): MyEdgePartition[ED2] = {
    // Faster than iter.toArray, because the expected size is known.
    val newData = new Array[ED2](data.length)
    var i = 0
    while (iter.hasNext) {
      newData (i) = iter.next ()
      i += 1
    }
    assert (newData.length == i)
    this.withData (newData)
  }

  val size: Int = localSrcIds.length

  def iterator: Iterator[Edge[ED]] = new Iterator[Edge[ED]] {
    private[this] val edge = new Edge[ED]
    private[this] var pos = 0

    override def hasNext: Boolean = pos < MyEdgePartition.this.size

    override def next(): Edge[ED] = {
      //      println(pos,srcIds(pos),dstIds(pos))
      edge.srcId = srcIds (pos)
      edge.dstId = dstIds (pos)
      edge.attr = data (pos)
      pos += 1
      edge
    }
  }

}
