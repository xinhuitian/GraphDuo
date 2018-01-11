package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.util.collection.{PrimitiveVector, Sorter}

/**
 * Created by sunny on 4/25/16.
 */
private[graphv]
class MyEdgePartitionBuilder[@specialized (Long, Int, Double) ED: ClassTag]
(size: Int = 64) {

  private[this] val edges = new PrimitiveVector[Edge[ED]](size)

  /** Add a new edge to the partition. */
  def add(src: VertexId, dst: VertexId, d: ED) {
    edges += Edge (src, dst, d)

  }

  def toEdgePartition: MyEdgePartition[ED] = {
    val edgeArray = edges.trim ().array
    new Sorter (Edge.edgeArraySortDataFormat [ED])
      .sort (edgeArray, 0, edgeArray.length, Edge.lexicographicOrdering)
    val localSrcIds = new Array[Int](edgeArray.length)
    val localDstIds = new Array[Int](edgeArray.length)
    val data = new Array[ED](edgeArray.length)
    val index = new GraphXPrimitiveKeyOpenHashMap[VertexId, Int]
    val global2local = new GraphXPrimitiveKeyOpenHashMap[VertexId, Int]
    val local2global = new PrimitiveVector[VertexId]
    // Copy edges into columnar structures, tracking the beginnings of source vertex id clusters and
    // adding them to the index. Also populate a map from vertex id to a sequential local offset.
    if (edgeArray.length > 0) {
      index.update (edgeArray (0).srcId, 0)
      var currSrcId: VertexId = edgeArray (0).srcId
      var currLocalId = -1
      var i = 0
      while (i < edgeArray.length) {
        val srcId = edgeArray (i).srcId
        val dstId = edgeArray (i).dstId
        localSrcIds (i) = global2local.changeValue (srcId, {
          currLocalId += 1; local2global += srcId; currLocalId
        }, identity)
        localDstIds (i) = global2local.changeValue (dstId, {
          currLocalId += 1; local2global += dstId; currLocalId
        }, identity)
        data (i) = edgeArray (i).attr
        if (srcId != currSrcId) {
          currSrcId = srcId
          index.update (currSrcId, i)
        }

        i += 1
      }
    }

    //        println("edge partition")
    //    var i = 0
    //        while (i < data.length){
    //          println(local2global(localSrcIds(i)),local2global(localDstIds(i)))
    //          i += 1
    //        }

    new MyEdgePartition (
      localSrcIds, localDstIds, data, index, global2local, local2global.trim ().array)
  }
}
