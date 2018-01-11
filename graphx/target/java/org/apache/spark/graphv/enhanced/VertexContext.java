package org.apache.spark.graphv.enhanced;
public abstract class VertexContext<VD extends java.lang.Object, ED extends java.lang.Object, A extends java.lang.Object> {
  // not preceding
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  public   VertexContext (org.apache.spark.graphv.EdgeDirection direction)  { throw new RuntimeException(); }
  public abstract  long srcId ()  ;
  /** The vertex id of the edge's destination vertex. */
  public abstract  long dstId ()  ;
  /** The vertex attribute of the edge's source vertex. */
  public abstract  VD srcAttr ()  ;
  public abstract  VD dstAttr ()  ;
  /** The attribute associated with the edge. */
  public abstract  ED attr ()  ;
  /** Sends a message to the destination vertex. */
  public abstract  void sendToDst (A msg)  ;
  public abstract  void sendToSrc (A msg)  ;
  public  org.apache.spark.graphv.EdgeDirection getDirection ()  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphVEdgeTriplet<VD, ED> toEdgeTriplet ()  { throw new RuntimeException(); }
}
