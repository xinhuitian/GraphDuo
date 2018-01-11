
package org.apache.spark.graphv.enhanced

import org.apache.spark.graphv._

abstract class VertexContext[VD, ED, A](
    direction: EdgeDirection = EdgeDirection.Out) {

  def srcId: VertexId

  /** The vertex id of the edge's destination vertex. */
  def dstId: VertexId

  /** The vertex attribute of the edge's source vertex. */
  def srcAttr: VD

  def dstAttr: VD

  /** The attribute associated with the edge. */
  def attr: ED

  /** Sends a message to the destination vertex. */
  def sendToDst(msg: A): Unit

  def sendToSrc(msg: A): Unit

  def getDirection: EdgeDirection = direction

  def toEdgeTriplet: GraphVEdgeTriplet[VD, ED] = {
    val et = new GraphVEdgeTriplet[VD, ED]
    et.srcId = srcId
    et.srcAttr = srcAttr
    et.dstAttr = dstAttr
    et.dstId = dstId
    et.attr = attr
    et
  }

  // def toMsgs: Array[Array[(VertexId, A)]]

}
