package org.apache.spark.graphv.enhanced;
public  class AggregatingVertexContext<VD extends java.lang.Object, ED extends java.lang.Object, A extends java.lang.Object> extends org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A> {
  static public  org.apache.spark.graphv.EdgeDirection getDirection ()  { throw new RuntimeException(); }
  static public  org.apache.spark.graphv.enhanced.GraphVEdgeTriplet<VD, ED> toEdgeTriplet ()  { throw new RuntimeException(); }
  // not preceding
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  public   AggregatingVertexContext (scala.Function2<A, A, A> mergeMsg, java.lang.Object aggregates, org.apache.spark.util.collection.BitSet bitset, int numPartitions, org.apache.spark.graphv.EdgeDirection direction)  { throw new RuntimeException(); }
  public  void set (long srcId, long dstId, int localSrcId, int localDstId, VD srcAttr, VD dstAttr, ED attr)  { throw new RuntimeException(); }
  public  void setSrcOnly (long srcId, int localSrcId, VD srcAttr)  { throw new RuntimeException(); }
  public  void setEdgeAndDst (ED edgeAttr, int localDstId, long dstId)  { throw new RuntimeException(); }
  public  void setRest (long dstId, int localDstId, VD dstAttr, ED attr)  { throw new RuntimeException(); }
  public  long srcId ()  { throw new RuntimeException(); }
  public  long dstId ()  { throw new RuntimeException(); }
  public  VD srcAttr ()  { throw new RuntimeException(); }
  public  VD dstAttr ()  { throw new RuntimeException(); }
  public  ED attr ()  { throw new RuntimeException(); }
  public  void sendToDst (A msg)  { throw new RuntimeException(); }
  public  void sendToSrc (A msg)  { throw new RuntimeException(); }
  private  void send (int localId, A msg)  { throw new RuntimeException(); }
}
