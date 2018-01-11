
package org.apache.spark.graphv.util

import org.apache.spark.util.collection.AppendOnlyMap

class GraphVAppendOnlyMap[K, V, C](
    createCombiner: V => C,
    mergeValue: (C, V) => C,
    mergeCombiners: (C, C) => C
) extends Iterable[(K, C)] {

  @volatile private var currentMap = new AppendOnlyMap[K, C]

  def insertAll(entries: Iterator[Product2[K, V]]): Unit = {
    var curEntry: Product2[K, V] = null
    val update: (Boolean, C) => C = (hadVal, oldVal) => {
      if (hadVal) mergeValue(oldVal, curEntry._2) else createCombiner(curEntry._2)
    }

    while (entries.hasNext) {
      curEntry = entries.next()
      currentMap.changeValue(curEntry._1, update)
    }
  }

  def insertAll(entries: Iterable[Product2[K, V]]): Unit = {
    insertAll(entries.iterator)
  }

  override def iterator: Iterator[(K, C)] = currentMap.iterator
}
