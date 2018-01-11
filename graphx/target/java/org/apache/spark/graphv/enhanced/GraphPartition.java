package org.apache.spark.graphv.enhanced;
public  class GraphPartition<VD extends java.lang.Object, ED extends java.lang.Object> implements scala.Serializable {
  public  int[] localDstIds ()  { throw new RuntimeException(); }
  public  java.lang.Object masterAttrs ()  { throw new RuntimeException(); }
  public  java.lang.Object mirrorAttrs ()  { throw new RuntimeException(); }
  public  int[] vertexIndex ()  { throw new RuntimeException(); }
  public  java.lang.Object edgeAttrs ()  { throw new RuntimeException(); }
  public  org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> global2local ()  { throw new RuntimeException(); }
  public  long[] local2global ()  { throw new RuntimeException(); }
  public  int indexStartPos ()  { throw new RuntimeException(); }
  public  int smallDegreeEndPos ()  { throw new RuntimeException(); }
  public  int largeDegreeMirrorEndPos ()  { throw new RuntimeException(); }
  public  int largeDegreeMasterEndPos ()  { throw new RuntimeException(); }
  public  int numPartitions ()  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.RoutingTable routingTable ()  { throw new RuntimeException(); }
  public  org.apache.spark.util.collection.BitSet activeSet ()  { throw new RuntimeException(); }
  // not preceding
  public   GraphPartition (int[] localDstIds, java.lang.Object masterAttrs, java.lang.Object mirrorAttrs, int[] vertexIndex, java.lang.Object edgeAttrs, org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> global2local, long[] local2global, int indexStartPos, int smallDegreeEndPos, int largeDegreeMirrorEndPos, int largeDegreeMasterEndPos, int numPartitions, org.apache.spark.graphv.enhanced.RoutingTable routingTable, org.apache.spark.util.collection.BitSet activeSet, scala.reflect.ClassTag<VD> evidence$7, scala.reflect.ClassTag<ED> evidence$8)  { throw new RuntimeException(); }
  public  int vertexAttrSize ()  { throw new RuntimeException(); }
  public  int masterSize ()  { throw new RuntimeException(); }
  public  int mirrorSize ()  { throw new RuntimeException(); }
  public  int srcMirrorSize ()  { throw new RuntimeException(); }
  public  int edgeSize ()  { throw new RuntimeException(); }
  public  int totalVertSize ()  { throw new RuntimeException(); }
  public  int thisPid ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> iterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<org.apache.spark.graphv.Edge<ED>> edgeIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> masterIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<java.lang.Object> mirrorIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<java.lang.Object> remoteLDMirrorIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<java.lang.Object> remoteOutMirrorIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<java.lang.Object> neighborIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<java.lang.Object> localNeighborIterator ()  { throw new RuntimeException(); }
  public  boolean isActive (int vid)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> activateAllMasters ()  { throw new RuntimeException(); }
  public  long getActiveNum ()  { throw new RuntimeException(); }
  public <VD2 extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphPartition<VD2, ED> mapVertices (scala.Function2<java.lang.Object, VD, VD2> f, boolean needActive, scala.reflect.ClassTag<VD2> evidence$9)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> mapMirrors (scala.Function2<java.lang.Object, VD, VD> f)  { throw new RuntimeException(); }
  public <ED2 extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphPartition<VD, ED2> mapTriplets (scala.Function1<org.apache.spark.graphv.enhanced.GraphVEdgeTriplet<VD, ED>, ED2> f, scala.reflect.ClassTag<ED2> evidence$10)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> joinLocalMsgs (scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> localMsgs, scala.Function3<java.lang.Object, VD, A, VD> vprog, scala.reflect.ClassTag<A> evidence$11)  { throw new RuntimeException(); }
  public <VD2 extends java.lang.Object> org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> localLeftJoin (org.apache.spark.graphv.enhanced.LocalFinalMessages<VD2> other, boolean needActive, scala.Function3<java.lang.Object, VD, scala.Option<VD2>, VD> f, scala.reflect.ClassTag<VD2> evidence$12)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, java.lang.Object>> shipMirrors ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, java.lang.Object>> sendRequests ()  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> syncMirrors (scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> msgs)  { throw new RuntimeException(); }
  public  org.apache.spark.util.collection.BitSet getDiffVertices (scala.Function2<java.lang.Object, VD, VD> initFunc)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.LocalMessageBlock<VD>>> generateSyncMsgsWithBitSet (org.apache.spark.util.collection.BitSet bitSet, boolean useSrc, boolean useDst)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.LocalMessageBlock<VD>>> generateSyncMsgs (boolean useSrc, boolean useDst)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.LocalMessageBlock<VD>>> generateSrcSyncMessages ()  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> syncSrcMirrors (scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.LocalMessageBlock<VD>>> msgs)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> syncSrcAndAggregateMessages (scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.LocalMessageBlock<VD>>> msgs, scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.enhanced.TripletFields tripletFields, scala.reflect.ClassTag<A> evidence$13)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> org.apache.spark.graphv.enhanced.LocalFinalMessages<A> generateLocalMsgs (scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> msgs, scala.Function2<A, A, A> reduceFunc, scala.reflect.ClassTag<A> evidence$14)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> org.apache.spark.graphv.enhanced.LocalFinalMessages<A> generateFinalMsgs (scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.MixMessageBlock<VD, A>>> msgs, scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, scala.reflect.ClassTag<A> evidence$15)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> localAggregate (scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> localMsgs, scala.Function2<A, A, A> reduceFunc, scala.reflect.ClassTag<A> evidence$16)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> globalAggregate (scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> localMsgs, scala.Function2<A, A, A> reduceFunc, scala.reflect.ClassTag<A> evidence$17)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> aggregateMessagesEdgeCentric (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.EdgeDirection edgeDirection, org.apache.spark.graphv.enhanced.TripletFields tripletFields, scala.reflect.ClassTag<A> evidence$18)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> aggregateMessagesVertexCentric2 (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.enhanced.TripletFields tripletFields, scala.reflect.ClassTag<A> evidence$19)  { throw new RuntimeException(); }
  public <A extends java.lang.Object> scala.collection.Iterator<scala.Tuple2<java.lang.Object, org.apache.spark.graphv.enhanced.MixMessageBlock<VD, A>>> aggregateMessagesVertexCentric (scala.Function1<org.apache.spark.graphv.enhanced.VertexContext<VD, ED, A>, scala.runtime.BoxedUnit> sendMsg, scala.Function2<A, A, A> mergeMsg, org.apache.spark.graphv.enhanced.TripletFields tripletFields, scala.reflect.ClassTag<A> evidence$20)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> withNewValues (java.lang.Object newValues)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> withMirrorValues (java.lang.Object newValues)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> withActiveSet (org.apache.spark.util.collection.BitSet newActiveSet)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.GraphPartition<VD, ED> withRoutingTable (org.apache.spark.graphv.enhanced.RoutingTable newRoutingTable)  { throw new RuntimeException(); }
}
