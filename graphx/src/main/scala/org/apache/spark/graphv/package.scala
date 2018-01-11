package org.apache.spark

import org.apache.spark.util.collection.OpenHashSet

/**
 * Created by XinhuiTian on 17/5/18.
 */
package object graphv {
  /**
   * A 64-bit vertex identifier that uniquely identifies a vertex within a graph. It does not need
   * to follow any ordering or any constraints other than uniqueness.
   */
  type VertexId = Long

  /** Integer identifier of a graph partition. Must be less than 2^30. */
  // TODO: Consider using Char.
  type PartitionID = Int

  private[graphv] type VertexSet = OpenHashSet[VertexId]

}
