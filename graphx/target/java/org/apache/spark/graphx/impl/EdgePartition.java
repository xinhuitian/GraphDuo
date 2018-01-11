package org.apache.spark.graphx.impl;
/**
 * A collection of edges, along with referenced vertex attributes and an optional active vertex set
 * for filtering computation on the edges.
 * <p>
 * The edges are stored in columnar format in <code>localSrcIds</code>, <code>localDstIds</code>, and <code>data</code>. All
 * referenced global vertex ids are mapped to a compact set of local vertex ids according to the
 * <code>global2local</code> map. Each local vertex id is a valid index into <code>vertexAttrs</code>, which stores the
 * corresponding vertex attribute, and <code>local2global</code>, which stores the reverse mapping to global
 * vertex id. The global vertex ids that are active are optionally stored in <code>activeSet</code>.
 * <p>
 * The edges are clustered by source vertex id, and the mapping from global vertex id to the index
 * of the corresponding edge cluster is stored in <code>index</code>.
 * <p>
 * @tparam ED the edge attribute type
 * @tparam VD the vertex attribute type
 * <p>
 * param:  localSrcIds the local source vertex id of each edge as an index into <code>local2global</code> and
 *   <code>vertexAttrs</code>
 * param:  localDstIds the local destination vertex id of each edge as an index into <code>local2global</code>
 *   and <code>vertexAttrs</code>
 * param:  data the attribute associated with each edge
 * param:  index a clustered index on source vertex id as a map from each global source vertex id to
 *   the offset in the edge arrays where the cluster for that vertex id begins
 * param:  global2local a map from referenced vertex ids to local ids which index into vertexAttrs
 * param:  local2global an array of global vertex ids where the offsets are local vertex ids
 * param:  vertexAttrs an array of vertex attributes where the offsets are local vertex ids
 * param:  activeSet an optional active vertex set for filtering computation on the edges
 */
public  class EdgePartition<ED extends java.lang.Object, VD extends java.lang.Object> implements scala.Serializable {
  // not preceding
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  public   EdgePartition (int[] localSrcIds, int[] localDstIds, java.lang.Object data, org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> index, org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> global2local, long[] local2global, java.lang.Object vertexAttrs, scala.Option<org.apache.spark.util.collection.OpenHashSet<java.lang.Object>> activeSet, scala.reflect.ClassTag<ED> evidence$1, scala.reflect.ClassTag<VD> evidence$2)  { throw new RuntimeException(); }
  /** No-arg constructor for serialization. */
  private   EdgePartition (scala.reflect.ClassTag<ED> evidence$3, scala.reflect.ClassTag<VD> evidence$4)  { throw new RuntimeException(); }
  /** Return a new `EdgePartition` with the specified edge data. */
  public <ED2 extends java.lang.Object> org.apache.spark.graphx.impl.EdgePartition<ED2, VD> withData (java.lang.Object data, scala.reflect.ClassTag<ED2> evidence$5)  { throw new RuntimeException(); }
  /** Return a new `EdgePartition` with the specified active set, provided as an iterator. */
  public  org.apache.spark.graphx.impl.EdgePartition<ED, VD> withActiveSet (scala.collection.Iterator<java.lang.Object> iter)  { throw new RuntimeException(); }
  /** Return a new `EdgePartition` with updates to vertex attributes specified in `iter`. */
  public  org.apache.spark.graphx.impl.EdgePartition<ED, VD> updateVertices (scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> iter)  { throw new RuntimeException(); }
  /** Return a new `EdgePartition` without any locally cached vertex attributes. */
  public <VD2 extends java.lang.Object> org.apache.spark.graphx.impl.EdgePartition<ED, VD2> withoutVertexAttributes (scala.reflect.ClassTag<VD2> evidence$6)  { throw new RuntimeException(); }
  private  long srcIds (int pos)  { throw new RuntimeException(); }
  private  long dstIds (int pos)  { throw new RuntimeException(); }
  private  ED attrs (int pos)  { throw new RuntimeException(); }
  /** Look up vid in activeSet, throwing an exception if it is None. */
  public  boolean isActive (long vid)  { throw new RuntimeException(); }
  /** The number of active vertices, if any exist. */
  public  scala.Option<java.lang.Object> numActives ()  { throw new RuntimeException(); }
  /**
   * Reverse all the edges in this partition.
   * <p>
   * @return a new edge partition with all edges reversed.
   */
  public  org.apache.spark.graphx.impl.EdgePartition<ED, VD> reverse ()  { throw new RuntimeException(); }
  /**
   * Construct a new edge partition by applying the function f to all
   * edges in this partition.
   * <p>
   * Be careful not to keep references to the objects passed to <code>f</code>.
   * To improve GC performance the same object is re-used for each call.
   * <p>
   * @param f a function from an edge to a new attribute
   * @tparam ED2 the type of the new attribute
   * @return a new edge partition with the result of the function <code>f</code>
   *         applied to each edge
   * @param evidence$7 (undocumented)
   */
  public <ED2 extends java.lang.Object> org.apache.spark.graphx.impl.EdgePartition<ED2, VD> map (scala.Function1<org.apache.spark.graphx.Edge<ED>, ED2> f, scala.reflect.ClassTag<ED2> evidence$7)  { throw new RuntimeException(); }
  /**
   * Construct a new edge partition by using the edge attributes
   * contained in the iterator.
   * <p>
   * @note The input iterator should return edge attributes in the
   * order of the edges returned by <code>EdgePartition.iterator</code> and
   * should return attributes equal to the number of edges.
   * <p>
   * @param iter an iterator for the new attribute values
   * @tparam ED2 the type of the new attribute
   * @return a new edge partition with the attribute values replaced
   * @param evidence$8 (undocumented)
   */
  public <ED2 extends java.lang.Object> org.apache.spark.graphx.impl.EdgePartition<ED2, VD> map (scala.collection.Iterator<ED2> iter, scala.reflect.ClassTag<ED2> evidence$8)  { throw new RuntimeException(); }
  /**
   * Construct a new edge partition containing only the edges matching <code>epred</code> and where both
   * vertices match <code>vpred</code>.
   * @param epred (undocumented)
   * @param vpred (undocumented)
   * @return (undocumented)
   */
  public  org.apache.spark.graphx.impl.EdgePartition<ED, VD> filter (scala.Function1<org.apache.spark.graphx.EdgeTriplet<VD, ED>, java.lang.Object> epred, scala.Function2<java.lang.Object, VD, java.lang.Object> vpred)  { throw new RuntimeException(); }
  /**
   * Apply the function f to all edges in this partition.
   * <p>
   * @param f an external state mutating user defined function.
   */
  public  void foreach (scala.Function1<org.apache.spark.graphx.Edge<ED>, scala.runtime.BoxedUnit> f)  { throw new RuntimeException(); }
  /**
   * Merge all the edges with the same src and dest id into a single
   * edge using the <code>merge</code> function
   * <p>
   * @param merge a commutative associative merge operation
   * @return a new edge partition without duplicate edges
   */
  public  org.apache.spark.graphx.impl.EdgePartition<ED, VD> groupEdges (scala.Function2<ED, ED, ED> merge)  { throw new RuntimeException(); }
  /**
   * Apply <code>f</code> to all edges present in both <code>this</code> and <code>other</code> and return a new <code>EdgePartition</code>
   * containing the resulting edges.
   * <p>
   * If there are multiple edges with the same src and dst in <code>this</code>, <code>f</code> will be invoked once for
   * each edge, but each time it may be invoked on any corresponding edge in <code>other</code>.
   * <p>
   * If there are multiple edges with the same src and dst in <code>other</code>, <code>f</code> will only be invoked
   * once.
   * @param other (undocumented)
   * @param f (undocumented)
   * @param evidence$9 (undocumented)
   * @param evidence$10 (undocumented)
   * @return (undocumented)
   */
  public <ED2 extends java.lang.Object, ED3 extends java.lang.Object> org.apache.spark.graphx.impl.EdgePartition<ED3, VD> innerJoin (org.apache.spark.graphx.impl.EdgePartition<ED2, ?> other, scala.Function4<java.lang.Object, java.lang.Object, ED, ED2, ED3> f, scala.reflect.ClassTag<ED2> evidence$9, scala.reflect.ClassTag<ED3> evidence$10)  { throw new RuntimeException(); }
  /**
   * The number of edges in this partition
   * <p>
   * @return size of the partition
   */
  public  int size ()  { throw new RuntimeException(); }
  /** The number of unique source vertices in the partition. */
  public  int indexSize ()  { throw new RuntimeException(); }
  /**
   * Get an iterator over the edges in this partition.
   * <p>
   * Be careful not to keep references to the objects from this iterator.
   * To improve GC performance the same object is re-used in <code>next()</code>.
   * <p>
   * @return an iterator over edges in the partition
   */
  public  scala.collection.Iterator<org.apache.spark.graphx.Edge<ED>> iterator ()  { throw new RuntimeException(); }
  /**
   * Get an iterator over the edge triplets in this partition.
   * <p>
   * It is safe to keep references to the objects from this iterator.
   * @param includeSrc (undocumented)
   * @param includeDst (undocumented)
   * @return (undocumented)
   */
  public  scala.collection.Iterator<org.apache.spark.graphx.EdgeTriplet<VD, ED>> tripletIterator (boolean includeSrc, boolean includeDst)  { throw new RuntimeException(); }
  /**
   * Send messages along edges and aggregate them at the receiving vertices. Implemented by scanning
   * all edges sequentially.
   * <p>
   * @param sendMsg generates messages to neighboring vertices of an edge
   * @param mergeMsg the combiner applied to messages destined to the same vertex
   * @param tripletFields which triplet fields <code>sendMsg</code> uses
   * @param activeness criteria for filtering edges based on activeness
   * <p>
   * @return iterator aggregated messages keyed by the receiving vertex id
   * @param evidence$11 (undocumented)
   */
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> aggregateMessagesEdgeScan (scala.Function1<org.apache.spark.graphx.EdgeContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphx.TripletFields tripletFields, org.apache.spark.graphx.impl.EdgeActiveness activeness, scala.reflect.ClassTag<A> evidence$11)  { throw new RuntimeException(); }
  /**
   * Send messages along edges and aggregate them at the receiving vertices. Implemented by
   * filtering the source vertex index, then scanning each edge cluster.
   * <p>
   * @param sendMsg generates messages to neighboring vertices of an edge
   * @param mergeMsg the combiner applied to messages destined to the same vertex
   * @param tripletFields which triplet fields <code>sendMsg</code> uses
   * @param activeness criteria for filtering edges based on activeness
   * <p>
   * @return iterator aggregated messages keyed by the receiving vertex id
   * @param evidence$12 (undocumented)
   */
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> aggregateMessagesIndexScan (scala.Function1<org.apache.spark.graphx.EdgeContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphx.TripletFields tripletFields, org.apache.spark.graphx.impl.EdgeActiveness activeness, scala.reflect.ClassTag<A> evidence$12)  { throw new RuntimeException(); }
}
