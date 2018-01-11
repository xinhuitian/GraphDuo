package org.apache.spark.graphv

import org.apache.spark._

import org.apache.spark.rdd.RDD

/**
 * Created by sunny on 5/5/16.
 */
// txh: change the type of vertex id
abstract class MyVertexMessage[VD](sc: SparkContext,
    deps: Seq[Dependency[_]]) extends RDD[(VertexId, VD)](sc, deps) {

  private[graphv] def partitionsRDD: RDD[MyShippableVertexPartition[VD]]

  override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  // txh: set the partitioner of this rdd
  override val partitioner =
    partitionsRDD.partitioner.orElse(Some(new HashPartitioner(getPartitions.length)))

  override def compute(part: Partition, context: TaskContext): Iterator[(VertexId, VD)] = {
    val p = firstParent [MyShippableVertexPartition[VD]].iterator (part, context)
    if (p.hasNext) {
      p.next ().iterator.map (_.copy ())
    } else {
      Iterator.empty
    }
  }
}
