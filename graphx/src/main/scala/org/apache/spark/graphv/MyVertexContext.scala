
package org.apache.spark.graphv

/**
 * Created by sunny on 5/4/16.
 */
abstract class MyVertexContext[VD, ED, A] {

  def srcId: VertexId

  /** The vertex id of the edge's destination vertex. */
  def dstId: VertexId

  /** The vertex attribute of the edge's source vertex. */
  def srcAttr: VD

  /** The attribute associated with the edge. */
  def attr: ED

  /** Sends a message to the destination vertex. */
  def sendToDst(msg: A): Unit

  def sendToSrc(msg: A): Unit

  def toEdgeTriplet: MyEdgeTriplet[VD, ED] = {
    val et = new MyEdgeTriplet[VD, ED]
    et.srcId = srcId
    et.srcAttr = srcAttr
    et.dstId = dstId
    et.attr = attr
    et
  }
}
