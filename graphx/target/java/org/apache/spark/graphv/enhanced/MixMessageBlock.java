package org.apache.spark.graphv.enhanced;
public  class MixMessageBlock<VD extends java.lang.Object, A extends java.lang.Object> implements scala.Serializable {
  public  int[] mirrorLVids ()  { throw new RuntimeException(); }
  public  java.lang.Object mirrorAttrs ()  { throw new RuntimeException(); }
  public  long[] targetVids ()  { throw new RuntimeException(); }
  public  java.lang.Object msgs ()  { throw new RuntimeException(); }
  // not preceding
  public   MixMessageBlock (int[] mirrorLVids, java.lang.Object mirrorAttrs, long[] targetVids, java.lang.Object msgs, scala.reflect.ClassTag<VD> evidence$3, scala.reflect.ClassTag<A> evidence$4)  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, A>> msgIterator ()  { throw new RuntimeException(); }
  public  scala.collection.Iterator<scala.Tuple2<java.lang.Object, VD>> syncIterator ()  { throw new RuntimeException(); }
}
