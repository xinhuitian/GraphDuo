package org.apache.spark.graphv.enhanced;
public  class GraphImpl$ implements scala.Serializable {
  /**
   * Static reference to the singleton instance of this Scala object.
   */
  public static final GraphImpl$ MODULE$ = null;
  public   GraphImpl$ ()  { throw new RuntimeException(); }
  public <VD extends java.lang.Object, ED extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphImpl<VD, ED> buildRoutingTable (org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.GraphPartition<VD, ED>>> partitions, boolean useDstMirror, scala.reflect.ClassTag<VD> evidence$20, scala.reflect.ClassTag<ED> evidence$21)  { throw new RuntimeException(); }
  public <VD extends java.lang.Object, ED extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphImpl<VD, java.lang.Object> buildGraph (org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, scala.collection.Iterable<java.lang.Object>>> edges, VD defaultVertexValue, int factor, boolean useDstMirror, scala.reflect.ClassTag<VD> evidence$22, scala.reflect.ClassTag<ED> evidence$23)  { throw new RuntimeException(); }
  public <VD extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphImpl<VD, java.lang.Object> fromEdgeList (org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, scala.collection.Iterable<java.lang.Object>>> edgeList, VD defaultVertexAttr, int factor, boolean useDstMirror, org.apache.spark.storage.StorageLevel edgeStorageLevel, org.apache.spark.storage.StorageLevel vertexStorageLevel, boolean enableMirror, scala.reflect.ClassTag<VD> evidence$24)  { throw new RuntimeException(); }
}
