package org.apache.spark.graphv.enhanced;
public  class GraphPartitonBuilder<VD extends java.lang.Object> {
  // not preceding
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  public   GraphPartitonBuilder (int degreeThreshold, int numPartitions, VD defaultVertexValue, scala.reflect.ClassTag<VD> evidence$1)  { throw new RuntimeException(); }
  public  org.apache.spark.HashPartitioner helperPartitioner ()  { throw new RuntimeException(); }
  /** Add a new edge to the partition. */
  public  void add (scala.collection.Iterator<scala.Tuple2<java.lang.Object, long[]>> locals, scala.collection.Iterator<scala.Tuple2<java.lang.Object, long[]>> remotes)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, java.lang.Object> toGraphPartition (int pid)  { throw new RuntimeException(); }
}
