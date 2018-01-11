package org.apache.spark.graphx.lib;
/**
 * Compute the number of triangles passing through each vertex.
 * <p>
 * The algorithm is relatively straightforward and can be computed in three steps:
 * <p>
 * <ul>
 * <li> Compute the set of neighbors for each vertex</li>
 * <li> For each edge compute the intersection of the sets and send the count to both vertices.</li>
 * <li> Compute the sum at each vertex and divide by two since each triangle is counted twice.</li>
 * </ul>
 * <p>
 * There are two implementations.  The default <code>TriangleCount.run</code> implementation first removes
 * self cycles and canonicalizes the graph to ensure that the following conditions hold:
 * <ul>
 * <li> There are no self edges</li>
 * <li> All edges are oriented (src is greater than dst)</li>
 * <li> There are no duplicate edges</li>
 * </ul>
 * However, the canonicalization procedure is costly as it requires repartitioning the graph.
 * If the input data is already in "canonical form" with self cycles removed then the
 * <code>TriangleCount.runPreCanonicalized</code> should be used instead.
 * <p>
 * <pre><code>
 * val canonicalGraph = graph.mapEdges(e =&gt; 1).removeSelfEdges().canonicalizeEdges()
 * val counts = TriangleCount.runPreCanonicalized(canonicalGraph).vertices
 * </code></pre>
 * <p>
 */
public  class TriangleCount {
  static public <VD extends java.lang.Object, ED extends java.lang.Object> org.apache.spark.graphx.Graph<java.lang.Object, ED> run (org.apache.spark.graphx.Graph<VD, ED> graph, scala.reflect.ClassTag<VD> evidence$1, scala.reflect.ClassTag<ED> evidence$2)  { throw new RuntimeException(); }
  static public <VD extends java.lang.Object, ED extends java.lang.Object> org.apache.spark.graphx.Graph<java.lang.Object, ED> runPreCanonicalized (org.apache.spark.graphx.Graph<VD, ED> graph, scala.reflect.ClassTag<VD> evidence$3, scala.reflect.ClassTag<ED> evidence$4)  { throw new RuntimeException(); }
}
