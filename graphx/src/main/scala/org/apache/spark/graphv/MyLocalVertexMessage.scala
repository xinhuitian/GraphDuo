package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark._

import org.apache.spark.rdd.RDD

/**
 * Created by XinhuiTian on 17/5/21.
 */
abstract class MyLocalVertexMessage[VD: ClassTag](sc: SparkContext,
    deps: Seq[Dependency[_]]) extends RDD[(Int, VD)](sc, deps) {

  private[graphv] def partitionsRDD: RDD[MyShippableLocalVertexPartition[VD]]

  override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  // txh: set the partitioner of this rdd
  override val partitioner =
    partitionsRDD.partitioner.orElse(Some(new HashPartitioner(getPartitions.length)))

  override def compute(part: Partition, context: TaskContext): Iterator[(Int, VD)] = {
    val p = firstParent [MyShippableLocalVertexPartition[VD]].iterator (part, context)
    if (p.hasNext) {
      p.next ().iterator.map (_.copy ())
    } else {
      Iterator.empty
    }
  }
}
