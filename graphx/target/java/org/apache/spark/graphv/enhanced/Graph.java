package org.apache.spark.graphv.enhanced;
public abstract class Graph<VD extends java.lang.Object, ED extends java.lang.Object> extends org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, VD>> {
  // not preceding
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  // TypeTree().setOriginal(TypeBoundsTree(TypeTree(), TypeTree()))
  public   Graph (org.apache.spark.SparkContext sc, scala.collection.Seq<org.apache.spark.Dependency<?>> deps, scala.reflect.ClassTag<VD> evidence$1, scala.reflect.ClassTag<ED> evidence$2)  { throw new RuntimeException(); }
   abstract  org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.GraphPartition<VD, ED>>> partitionsRDD ()  ;
  protected  org.apache.spark.Partition[] getPartitions ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> compute (org.apache.spark.Partition part, org.apache.spark.TaskContext context)  { throw new RuntimeException(); }
  public abstract  org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, VD>> vertices ()  ;
  public abstract  org.apache.spark.rdd.RDD<org.apache.spark.graphv.Edge<ED>> edges ()  ;
  public abstract  int edgeSize ()  ;
  public abstract  long getActiveNums ()  ;
  public abstract  org.apache.spark.graphv.enhanced.Graph<VD, ED> activateAllMasters ()  ;
  public abstract <VD2 extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD2, ED> mapVertices (scala.Function2<java.lang.Object, VD, VD2> f, boolean needActive, scala.reflect.ClassTag<VD2> evidence$3)  ;
  public abstract  org.apache.spark.graphv.enhanced.Graph<VD, ED> mapMirrors (scala.Function2<java.lang.Object, VD, VD> f)  ;
  public abstract <VD2 extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD, ED> localOuterJoin (org.apache.spark.rdd.RDD<org.apache.spark.graphv.enhanced.LocalFinalMessages<VD2>> other, boolean needActive, scala.Function3<java.lang.Object, VD, scala.Option<VD2>, VD> updateF, scala.reflect.ClassTag<VD2> evidence$4)  ;
  public abstract  org.apache.spark.graphv.enhanced.Graph<VD, ED> syncSrcMirrors ()  ;
  public <A extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD, ED> mapReduceTriplets (scala.Function1<org.apache.spark.graphv.enhanced.GraphVEdgeTriplet<VD, ED>, scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>>> mapFunc, scala.Function2<A, A, A> reduceFunc, scala.Function3<java.lang.Object, VD, A, VD> updateFunc, org.apache.spark.graphv.EdgeDirection activeEdgeDirection, boolean needActive, boolean edgeFilter, scala.reflect.ClassTag<A> evidence$5)  { throw new RuntimeException(); }
  public abstract <A extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD, ED> compute (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, scala.Function3<java.lang.Object, VD, A, VD> vFunc, org.apache.spark.graphv.EdgeDirection edgeDirection, org.apache.spark.graphv.enhanced.TripletFields tripletFields, boolean needActive, scala.reflect.ClassTag<A> evidence$6)  ;
  public abstract <A extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD, ED> compute2 (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, scala.Function3<java.lang.Object, VD, A, VD> vFunc, org.apache.spark.graphv.EdgeDirection edgeDirection, boolean needActive, boolean edgeFilter, scala.reflect.ClassTag<A> evidence$7)  ;
  public abstract <A extends java.lang.Object> org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, A>> aggregateLocalMessages (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.EdgeDirection edgeDirection, org.apache.spark.graphv.enhanced.TripletFields tripletFields, boolean needActive, scala.reflect.ClassTag<A> evidence$8)  ;
  public abstract <A extends java.lang.Object> org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, A>> aggregateGlobalMessages (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.EdgeDirection edgeDirection, org.apache.spark.graphv.enhanced.TripletFields tripletFields, boolean needActive, scala.reflect.ClassTag<A> evidence$9)  ;
  public abstract <A extends java.lang.Object> org.apache.spark.rdd.RDD<org.apache.spark.graphv.enhanced.LocalFinalMessages<A>> aggregateMessages (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.EdgeDirection edgeDirection, org.apache.spark.graphv.enhanced.TripletFields tripletFields, boolean needActive, scala.reflect.ClassTag<A> evidence$10)  ;
  public  org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, java.lang.Object>> outDegrees ()  { throw new RuntimeException(); }
  public  org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, java.lang.Object>> inDegrees ()  { throw new RuntimeException(); }
  public  org.apache.spark.rdd.RDD<org.apache.spark.graphv.enhanced.LocalFinalMessages<java.lang.Object>> localOutDegrees ()  { throw new RuntimeException(); }
  public  org.apache.spark.rdd.RDD<org.apache.spark.graphv.enhanced.LocalFinalMessages<java.lang.Object>> localInDegrees ()  { throw new RuntimeException(); }
  public abstract <ED2 extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD, ED2> mapTriplets (scala.Function1<org.apache.spark.graphv.enhanced.GraphVEdgeTriplet<VD, ED>, ED2> map, org.apache.spark.graphv.enhanced.TripletFields tripletFields, scala.reflect.ClassTag<ED2> evidence$11)  ;
  public abstract  org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, java.lang.Object>> degreeRDD (boolean inDegree)  ;
  public abstract  org.apache.spark.rdd.RDD<org.apache.spark.graphv.enhanced.LocalFinalMessages<java.lang.Object>> localDegreeRDD (boolean inDegree)  ;
  public abstract <VD2 extends java.lang.Object, ED2 extends java.lang.Object> org.apache.spark.graphv.enhanced.Graph<VD2, ED2> withPartitionsRDD (org.apache.spark.rdd.RDD<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.GraphPartition<VD2, ED2>>> partitionsRDD, scala.reflect.ClassTag<VD2> evidence$12, scala.reflect.ClassTag<ED2> evidence$13)  ;
}
