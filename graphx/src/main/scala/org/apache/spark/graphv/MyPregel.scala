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

import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD

/**
 * Created by sunny on 5/6/16.
 * TXH: change the order in each iteration
 */
object MyPregel extends Logging {

  def apply[VD: ClassTag, ED: ClassTag, A: ClassTag]
  (graph: MyGraph[VD, ED],
      initialMsg: A,
      maxIterations: Int = Int.MaxValue,
      activeDirection: EdgeDirection = EdgeDirection.Either,
      needActive: Boolean = false)
    (vprog: (VertexId, VD, A) => VD,
        sendMsg: MyEdgeTriplet[VD, ED] => Iterator[(VertexId, A)],
        mergeMsg: (A, A) => A)
  : MyGraph[VD, ED] = {
    require (maxIterations > 0, s"Maximum of iterations must be greater than 0," +
      s" but got ${maxIterations}")

    // graph init
    var g = graph.mapVertices ((vid, vdata) => vprog (vid, vdata, initialMsg)).cache()

    // compute the messages
    // var messages = g.mapReduceTriplets (sendMsg, mergeMsg).cache()
    // var activeMessages = messages.count ()
    var activeNums = g.getActiveNums
    println("Vertex activeNums: " + activeNums)
    // Loop
    var prevG: MyGraph[VD, ED] = null
    var i = 0
    while (activeNums > 0 && (needActive || i < maxIterations)) {
      val jobStartTime = System.currentTimeMillis
      // Receive the messages and update the vertices.
      prevG = g
      //      println("iteration  " +i +"  It took %d ms count graph vertices".format(System.currentTimeMillis -
      // startTime))
      //      val oldMessages = messages
      // val oldMessages = messages

      println("Vertex activeNums: " + activeNums)
      val messages = g.mapReduceTriplets (sendMsg, mergeMsg).cache()
      val msgNum = messages.count
      println("Messages: " + msgNum)
      g = g.joinLocalVertices (messages, needActive)(vprog).cache()

      // g.vertices.foreach(println)
      // g.vertices.count()
      // activeMessages = messages.count()
      activeNums = g.getActiveNums

      // println(g.vertices.map(_._2).asInstanceOf[RDD[Double]].sum())
      println ("iteration  " + i + "  It took %d ms count message".format (System.currentTimeMillis - jobStartTime))

      // messages.unpersist(blocking = false)
      prevG.unpersistVertices(blocking = false)
      i += 1
    }
    //    messages.unpersist(blocking = false)
    g
  } // end of apply
}
