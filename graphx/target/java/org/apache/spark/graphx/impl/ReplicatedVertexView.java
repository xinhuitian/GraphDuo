package org.apache.spark.graphx.impl;
/**
 * Manages shipping vertex attributes to the edge partitions of an
 * {@link org.apache.spark.graphx.EdgeRDD}. Vertex attributes may be partially shipped to construct a
 * triplet view with vertex attributes on only one side, and they may be updated. An active vertex
 * set may additionally be shipped to the edge partitions. Be careful not to store a reference to
 * <code>edges</code>, since it may be modified when the attribute shipping level is upgraded.
 */
  class ReplicatedVertexView<VD extends java.lang.Object, ED extends java.lang.Object> {
  public  org.apache.spark.graphx.impl.EdgeRDDImpl<ED, VD> edges ()  { throw new RuntimeException(); }
  public  boolean hasSrcId ()  { throw new RuntimeException(); }
  public  boolean hasDstId ()  { throw new RuntimeException(); }
  // not preceding
  public   ReplicatedVertexView (org.apache.spark.graphx.impl.EdgeRDDImpl<ED, VD> edges, boolean hasSrcId, boolean hasDstId, scala.reflect.ClassTag<VD> evidence$1, scala.reflect.ClassTag<ED> evidence$2)  { throw new RuntimeException(); }
  /**
   * Return a new <code>ReplicatedVertexView</code> with the specified <code>EdgeRDD</code>, which must have the same
   * shipping level.
   * @param _edges (undocumented)
   * @param evidence$3 (undocumented)
   * @param evidence$4 (undocumented)
   * @return (undocumented)
   */
  public <VD2 extends java.lang.Object, ED2 extends java.lang.Object> org.apache.spark.graphx.impl.ReplicatedVertexView<VD2, ED2> withEdges (org.apache.spark.graphx.impl.EdgeRDDImpl<ED2, VD2> _edges, scala.reflect.ClassTag<VD2> evidence$3, scala.reflect.ClassTag<ED2> evidence$4)  { throw new RuntimeException(); }
  /**
   * Return a new <code>ReplicatedVertexView</code> where edges are reversed and shipping levels are swapped to
   * match.
   * @return (undocumented)
   */
  public  org.apache.spark.graphx.impl.ReplicatedVertexView<VD, ED> reverse ()  { throw new RuntimeException(); }
  /**
   * Upgrade the shipping level in-place to the specified levels by shipping vertex attributes from
   * <code>vertices</code>. This operation modifies the <code>ReplicatedVertexView</code>, and callers can access <code>edges</code>
   * afterwards to obtain the upgraded view.
   * @param vertices (undocumented)
   * @param includeSrc (undocumented)
   * @param includeDst (undocumented)
   */
  public  void upgrade (org.apache.spark.graphx.VertexRDD<VD> vertices, boolean includeSrc, boolean includeDst)  { throw new RuntimeException(); }
  /**
   * Return a new <code>ReplicatedVertexView</code> where the <code>activeSet</code> in each edge partition contains only
   * vertex ids present in <code>actives</code>. This ships a vertex id to all edge partitions where it is
   * referenced, ignoring the attribute shipping level.
   * @param actives (undocumented)
   * @return (undocumented)
   */
  public  org.apache.spark.graphx.impl.ReplicatedVertexView<VD, ED> withActiveSet (org.apache.spark.graphx.VertexRDD<?> actives)  { throw new RuntimeException(); }
  /**
   * Return a new <code>ReplicatedVertexView</code> where vertex attributes in edge partition are updated using
   * <code>updates</code>. This ships a vertex attribute only to the edge partitions where it is in the
   * position(s) specified by the attribute shipping level.
   * @param updates (undocumented)
   * @return (undocumented)
   */
  public  org.apache.spark.graphx.impl.ReplicatedVertexView<VD, ED> updateVertices (org.apache.spark.graphx.VertexRDD<VD> updates)  { throw new RuntimeException(); }
}
