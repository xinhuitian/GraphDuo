/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap
import org.apache.spark.util.collection.{BitSet, PrimitiveVector}

/**
 * Created by sunny on 4/27/16.
 */
/*
class MyVertexPartitionBuilder[@specialized (Long, Int, Double) VD: ClassTag](
    size: Int = 64) {

  // store edges
  private[this] val edges = new PrimitiveVector[Edge[Int]]
  private[this] var vertexSize = 0
  // private[this] var edgeSize = 0

  /** Add a new edge to the partition. */
  def add(edge: (VertexId, VertexId)) {
    edges += new Edge(edge._1, edge._2, 1)

    // edgeSize += 1
  }

  def toVertexPartition: MyVertexPartition[VD, Int] = {
    // 1. sort the edges based on the srcId
    val edgeArray = edges.iterator.toArray
        new Sorter(Edge.edgeArraySortDataFormat[Int])
          .sort(edgeArray, 0, edgeArray.length, Edge.lexicographicOrdering)
    // println ("vertexSize:" + vertexSize)
    // store the dstIds
    // println("edgeArray")
    // edgeArray.foreach(println)
    val dstIds = new Array[VertexId](edgeArray.length)
    val global2local = new GraphXPrimitiveKeyOpenHashMap[VertexId, Int]
    val local2global = new PrimitiveVector[VertexId]
    val edgeAttrs = new Array[Int](edgeArray.length)
    // store the start offset and the length of dstIds for each srcId
    val index = new GraphXPrimitiveKeyOpenHashMap[VertexId, (Int, Int)]
    var currLocalId = -1

    if (edgeArray.size > 0) {
      var i = 0
      var p = 0 // dst start pos
      var p_length = 0 // dsts length
      // var start_pos = 0
      edgeArray.foreach { edge =>
        global2local.changeValue (edge.srcId, {
          currLocalId += 1; local2global += edge.srcId; currLocalId
        }, identity)
      }

      vertexSize = global2local.size

      // println("After add srcIds")
      // global2local.foreach(println)

      var currSrcId: VertexId = edgeArray(0).srcId
      // println("currentSrcId: " + currSrcId)
      while (i < edgeArray.size) {
        val srcId = edgeArray(i).srcId
        val dstId = edgeArray(i).dstId

        if (dstId >= 0) { // only consider the real edge

          edgeAttrs (p + p_length) = 1
          dstIds (p + p_length) = dstId

          global2local.changeValue(dstId,
          { currLocalId += 1; local2global += dstId; currLocalId }, identity)

          if (currSrcId != srcId) { // not the same srcId
            index.update (currSrcId, (p, p_length))
            currSrcId = srcId
            p = p + p_length
            p_length = 0
          }
          p_length += 1
          // final one
        }

        if (i == edgeArray.size - 1) {
          index.update (currSrcId, (p, p_length))
        }
        i += 1

      }

      /*

      val leftVs = edgeArray.filter(e => e.dstId == -1)
      leftVs.foreach { e => index.changeValue(e.srcId,
        (0, 0), identity)}
        */
    }

    val active = new BitSet (local2global.size)
    active.setUntil (local2global.size)
    new MyVertexPartition (
      dstIds, new Array[VD](vertexSize),
      index, edgeAttrs, global2local,
      local2global.trim ().array, active)
  }
}
*/

class MyVertexPartitionBuilder[@specialized (Long, Int, Double) VD: ClassTag]
(size: Int = 64) {

  private[this] val vertex = new PrimitiveVector[(VertexId, Iterable[VertexId])]
  private[this] var edgeSize = 0
  private[this] var masterSize = 0

  /** Add a new edge to the partition. */
  def add(v: (VertexId, Iterable[VertexId])) {

    vertex += v

    // vertexSize += v._2.size - 1
    edgeSize += v._2.filter(_ != -1).size
  }

  def toVertexPartition: MyVertexPartition[VD, Int] = {
    //    val edgeArray = edges.trim().array
    //    new Sorter(Edge.edgeArraySortDataFormat[ED])
    //      .sort(edgeArray, 0, edgeArray.length, Edge.lexicographicOrdering)
    // println ("edgeSize:" + edgeSize)
    // all the vertices for data transfer
    val dstIds = new Array[VertexId](edgeSize)

    val localDstIds = new Array[Int](edgeSize)

    // the global local transfer only is used for msg generate and msg merge
    val global2local = new GraphXPrimitiveKeyOpenHashMap[VertexId, Int]
    val local2global = new PrimitiveVector[VertexId]
    val edgeAttrs = new Array[Int](edgeSize)
    // init the index using (-1, -1)

    var currLocalId = -1

    var index = Array.empty[(Int, Int)]

    if (vertex.length > 0) {
      var i = 0
      var p = 0
      var p_pos = 0
      // add masters to the global2local and local2global index
      vertex.iterator.map(_._1).foreach (vid => {
        global2local.changeValue (vid, {
          currLocalId += 1
          local2global += vid
          currLocalId
        }, identity)
      })

      /*
      vertex.iterator.foreach { vid =>
        print(vid._1 + " (")
        vid._2.foreach(v => print(v + " "))
        println(")")
      }
      */

      masterSize = local2global.size

      // println("MasterSize: " + masterSize)
      index = Array.fill[(Int, Int)](masterSize)((-1, -1))

      // add the dst ids to the global2local and local2global indexes
      while (i < vertex.length) {
        val srcId = vertex (i)._1
        val localSrcId = global2local(srcId)

        vertex (i)._2.foreach (dst => {
          // println(s"edges: ($srcId, $dst)")
          if (dst >= 0) {
            val dstId = dst
            dstIds (p) = dstId
            global2local.changeValue (dstId, {
              currLocalId += 1
              local2global += dstId
              currLocalId
            }, identity)
            // bug: the dstId can be one of the srcId
            // localDstIds(p) = currLocalId
            edgeAttrs (p) = 1
            p += 1
            p_pos += 1
          }
        })

        index.update(localSrcId, (p - p_pos, p_pos))
        p_pos = 0
        i += 1
      }
    }
    // println("local2global size: " + local2global.size)

    for (i <- 0 until edgeSize) {
      localDstIds(i) = global2local(dstIds(i))
    }


    val active = new BitSet (masterSize)
    active.setUntil (masterSize)
    new MyVertexPartition (
      localDstIds, new Array[VD](masterSize),
      index, edgeAttrs, global2local,
      local2global.trim ().array, active)
  }
}
