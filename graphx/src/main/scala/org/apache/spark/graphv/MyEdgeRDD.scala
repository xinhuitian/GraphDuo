package org.apache.spark.graphv

import scala.reflect.ClassTag

import org.apache.spark.{Dependency, Partition, SparkContext, TaskContext}

import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by sunny on 4/26/16.
 */
abstract class MyEdgeRDD[ED](
    sc: SparkContext,
    deps: Seq[Dependency[_]]) extends RDD[Edge[ED]](sc, deps) {
  // scalastyle:off structural.type
  private[graphv] def partitionsRDD: RDD[(PartitionID, MyEdgePartition[ED])]

  override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  override def compute(part: Partition, context: TaskContext): Iterator[Edge[ED]] = {
    val p = firstParent [(PartitionID, MyEdgePartition[ED])].iterator (part, context)
    if (p.hasNext) {
      p.next ()._2.iterator.map (_.copy ())
    } else {
      Iterator.empty
    }
  }

  /**
   * Map the values in an edge partitioning preserving the structure but changing the values.
   *
   * @tparam ED2 the new edge value type
   * @param f the function from an edge to a new edge value
   * @return a new EdgeRDD containing the new edge values
   */
  def mapValues[ED2: ClassTag](f: Edge[ED] => ED2): MyEdgeRDD[ED2]

  private[graphv] def withTargetStorageLevel(targetStorageLevel: StorageLevel): MyEdgeRDD[ED]
}

object MyEdgeRDD {
  /**
   * Creates an EdgeRDD from a set of edges.
   *
   * @tparam ED the edge attribute type
   */
  def fromEdges[ED: ClassTag](edges: RDD[Edge[ED]]): MyEdgeRDDImpl[ED] = {
    val edgePartitions = edges.mapPartitionsWithIndex { (pid, iter) =>
      val builder = new MyEdgePartitionBuilder[ED]
      iter.foreach{e =>
        builder.add (e.srcId, e.dstId, e.attr)
      }
      Iterator ((pid, builder.toEdgePartition))
    }

    MyEdgeRDD.fromEdgePartitions (edgePartitions)
  }

  /**
   * Creates an EdgeRDD from already-constructed edge partitions.
   *
   * @tparam ED the edge attribute type
   */
  private[graphv] def fromEdgePartitions[ED: ClassTag]
  (edgePartitions: RDD[(Int, MyEdgePartition[ED])]): MyEdgeRDDImpl[ED] = {

    /*
    val dst2p = edgePartitions.mapPartitions { iter =>
      val (pid, part) = iter.next()

    }
    */
    new MyEdgeRDDImpl (edgePartitions)
  }

}