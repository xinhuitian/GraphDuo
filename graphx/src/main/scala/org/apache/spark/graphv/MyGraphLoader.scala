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

import org.apache.spark.{HashPartitioner, SparkContext}

import org.apache.spark.internal.Logging
import org.apache.spark.storage.StorageLevel

/**
 * Created by sunny on 4/25/16.
 */
object MyGraphLoader extends Logging {
  def edgeListFile(
      sc: SparkContext,
      path: String,
      reverse: Boolean = false,
      numVertexPartitions: Int = -1,
      edgeStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY,
      vertexStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY)
  : MyGraph[Int, Int] = {
    val startTime = System.currentTimeMillis

    // Parse the edge data table directly into edge partitions
    val lines =
      if (numVertexPartitions > 0) {
        sc.textFile (path, numVertexPartitions).coalesce (numVertexPartitions)
      } else {
        sc.textFile (path)
      }

    // using cache cause longer time?
    val filteredLines = lines.filter(line => !line.isEmpty && line(0) != '#')


    val mid_data = filteredLines.flatMap (line => {
      val parts = line.split ("\\s+")
      Iterator((parts(0).toLong, parts(1).toLong),
        (parts(1).toLong, -1L), (parts(0).toLong, -1L))
    })

    // mid_data.foreach(println)

    // groupby the master vids
    val links = mid_data.groupByKey (new HashPartitioner (numVertexPartitions))

    // val links = mid_data.partitionBy(new HashPartitioner(numVertexPartitions)).cache()
    println ("It took %d ms to group".format (System.currentTimeMillis - startTime))
    // 添加出度为0的节点的邻接表项 如：(4,()) (5,())...

    MyGraphImpl.fromEdgeList(links, defaultVertexAttr = 1, edgeStorageLevel = edgeStorageLevel,
      vertexStorageLevel = vertexStorageLevel)
  } // end of edgeListFile
}
