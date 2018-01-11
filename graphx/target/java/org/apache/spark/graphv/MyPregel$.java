package org.apache.spark.graphv;
/**
 * Created by sunny on 5/6/16.
 * TXH: change the order in each iteration
 */
public  class MyPregel$ implements org.apache.spark.internal.Logging {
  /**
   * Static reference to the singleton instance of this Scala object.
   */
  public static final MyPregel$ MODULE$ = null;
  public   MyPregel$ ()  { throw new RuntimeException(); }
  public <VD extends java.lang.Object, ED extends java.lang.Object, A extends java.lang.Object> org.apache.spark.graphv.MyGraph<VD, ED> apply (org.apache.spark.graphv.MyGraph<VD, ED> graph, A initialMsg, int maxIterations, org.apache.spark.graphv.EdgeDirection activeDirection, boolean needActive, scala.Function3<java.lang.Object, VD, A, VD> vprog, scala.Function1<org.apache.spark.graphv.MyEdgeTriplet<VD, ED>, scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>>> sendMsg, scala.Function2<A, A, A> mergeMsg, scala.reflect.ClassTag<VD> evidence$1, scala.reflect.ClassTag<ED> evidence$2, scala.reflect.ClassTag<A> evidence$3)  { throw new RuntimeException(); }
}
