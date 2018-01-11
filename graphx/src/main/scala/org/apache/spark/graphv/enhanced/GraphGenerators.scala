
package org.apache.spark.graphv.enhanced

import org.apache.spark.graphv._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext

object GraphGenerators {

  /*
  def gridGraph(sc: SparkContext, rows: Int, cols: Int): Graph[(Int, Int), Double] = {
    def sub2ind(r: Int, c: Int): VertexId = r * cols + c

    val vertices: RDD[(VertexId, (Int, Int))] = sc.parallelize(0 until rows).flatMap {
      r => (0 until cols).map(c => (sub2ind(r, c), (r, c)))
    }

    val edges: RDD[Edge[Double]] = vertices.flatMap{ case (vid, (r, c)) =>
      (if (r + 1 < rows) { Seq( (sub2ind(r, c), sub2ind(r + 1, c))) } else { Seq.empty }) ++
      (if (c + 1 < cols) { Seq( (sub2ind(r, c), sub2ind(r, c + 1))) } else { Seq.empty })
    }.map{ case (src, dst) => Edge(src, dst, 1.0) }


  }
  */
}
