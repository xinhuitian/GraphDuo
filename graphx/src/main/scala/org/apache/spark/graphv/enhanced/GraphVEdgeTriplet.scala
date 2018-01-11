
package org.apache.spark.graphv.enhanced

import org.apache.spark.graphv.Edge

class GraphVEdgeTriplet[VD, ED] extends Edge[ED] {

  var srcAttr: VD = _

  var dstAttr: VD = _

  def set(otherSrcId: Long, otherDstId: Long, otherEdgeAttr: ED)
  : GraphVEdgeTriplet[VD, ED] = {
    srcId = otherSrcId
    dstId = otherDstId
    attr = otherEdgeAttr
    this
  }

  def set(edge: Edge[ED]): GraphVEdgeTriplet[VD, ED] = {
    srcId = edge.srcId
    dstId = edge.dstId
    attr = edge.attr
    this
  }
}
