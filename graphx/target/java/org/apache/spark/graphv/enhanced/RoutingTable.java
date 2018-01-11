package org.apache.spark.graphv.enhanced;
public  class RoutingTable implements scala.Serializable {
  static private  scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> toMessage (long vid, int localId, int pid, byte position)  { throw new RuntimeException(); }
  static private  long vidFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  static private  int pidFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  static private  byte positionFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  static private  int localIdFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  static public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>>> generateMessagesWithPos (int pid, org.apache.spark.graphv.enhanced.GraphPartition<?, ?> graphPartition, boolean needSrc, boolean needDst)  { throw new RuntimeException(); }
  static public  org.apache.spark.graphv.enhanced.RoutingTable fromMsgs (int numPartitions, scala.collection.Iterator<scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>>> iter, org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> g2lMap)  { throw new RuntimeException(); }
  static private  org.apache.spark.util.collection.BitSet toBitSet (org.apache.spark.util.collection.PrimitiveVector<java.lang.Object> flags)  { throw new RuntimeException(); }
  private  scala.Tuple3<scala.Tuple2<java.lang.Object, java.lang.Object>[], org.apache.spark.util.collection.BitSet, org.apache.spark.util.collection.BitSet>[] routingTable ()  { throw new RuntimeException(); }
  // not preceding
  public   RoutingTable (scala.Tuple3<scala.Tuple2<java.lang.Object, java.lang.Object>[], org.apache.spark.util.collection.BitSet, org.apache.spark.util.collection.BitSet>[] routingTable)  { throw new RuntimeException(); }
  public  int numPartitions ()  { throw new RuntimeException(); }
  public  int partitionSize (int pid)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, java.lang.Object>> iterator ()  { throw new RuntimeException(); }
  public  void foreachWithPartition (int pid, boolean includeSrc, boolean includeDst, scala.Function1<scala.Tuple2<java.lang.Object, java.lang.Object>, scala.runtime.BoxedUnit> f)  { throw new RuntimeException(); }
}
