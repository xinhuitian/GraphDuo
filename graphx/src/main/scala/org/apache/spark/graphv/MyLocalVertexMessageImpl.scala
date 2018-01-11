package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.OneToOneDependency

import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by XinhuiTian on 17/5/21.
 */
class MyLocalVertexMessageImpl[VD: ClassTag](@transient val partitionsRDD: RDD[MyShippableLocalVertexPartition[VD]],
    val targetStorageLevel: StorageLevel = StorageLevel.MEMORY_ONLY)

  extends MyLocalVertexMessage[VD](partitionsRDD.context, List (new OneToOneDependency (partitionsRDD))) {

}
