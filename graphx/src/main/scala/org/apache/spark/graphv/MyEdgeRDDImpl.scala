package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.{HashPartitioner, OneToOneDependency}

import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by sunny on 4/25/16.
 */
class MyEdgeRDDImpl[ED: ClassTag] private[graphv]
(
    @transient override val partitionsRDD: RDD[(PartitionID, MyEdgePartition[ED])],
    val targetStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY)
  extends MyEdgeRDD[ED](partitionsRDD.context, List (new OneToOneDependency (partitionsRDD))) {

  override val partitioner =
    partitionsRDD.partitioner.orElse (Some (new HashPartitioner (partitions.length)))

  override def mapValues[ED2: ClassTag](f: Edge[ED] => ED2): MyEdgeRDDImpl[ED2] =
    mapEdgePartitions ((pid, part) => part.map (f))

  def mapEdgePartitions[ED2: ClassTag](
      f: (PartitionID, MyEdgePartition[ED]) => MyEdgePartition[ED2]): MyEdgeRDDImpl[ED2] = {
    this.withPartitionsRDD [ED2](partitionsRDD.mapPartitions ({iter =>
      if (iter.hasNext) {
        val (pid, ep) = iter.next ()
        Iterator (Tuple2 (pid, f (pid, ep)))
      } else {
        Iterator.empty
      }
    }, preservesPartitioning = true))
  }

  private[graphv] def withPartitionsRDD[ED2: ClassTag](partitionsRDD: RDD[(PartitionID, MyEdgePartition[ED2])]):
  MyEdgeRDDImpl[ED2] = {
    new MyEdgeRDDImpl (partitionsRDD, this.targetStorageLevel)
  }

  override def withTargetStorageLevel(storageLevel: StorageLevel): MyEdgeRDD[ED] = {
    new MyEdgeRDDImpl (this.partitionsRDD, storageLevel)
  }
}
