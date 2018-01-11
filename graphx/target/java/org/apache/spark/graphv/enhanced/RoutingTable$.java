package org.apache.spark.graphv.enhanced;
public  class RoutingTable$ implements scala.Serializable {
  /**
   * Static reference to the singleton instance of this Scala object.
   */
  public static final RoutingTable$ MODULE$ = null;
  public   RoutingTable$ ()  { throw new RuntimeException(); }
  private  scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> toMessage (long vid, int localId, int pid, byte position)  { throw new RuntimeException(); }
  private  long vidFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  private  int pidFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  private  byte positionFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  private  int localIdFromMessage (scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>> msg)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>>> generateMessagesWithPos (int pid, org.apache.spark.graphv.enhanced.GraphPartition<?, ?> graphPartition, boolean needSrc, boolean needDst)  { throw new RuntimeException(); }
  public  org.apache.spark.graphv.enhanced.RoutingTable fromMsgs (int numPartitions, scala.collection.Iterator<scala.Tuple2<java.lang.Object, scala.Tuple2<java.lang.Object, java.lang.Object>>> iter, org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap<java.lang.Object, java.lang.Object> g2lMap)  { throw new RuntimeException(); }
  private  org.apache.spark.util.collection.BitSet toBitSet (org.apache.spark.util.collection.PrimitiveVector<java.lang.Object> flags)  { throw new RuntimeException(); }
}
