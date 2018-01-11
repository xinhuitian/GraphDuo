
package org.apache.spark.graphv.enhanced

sealed class TripletFields(
    val useSrc: Boolean,
    val useDst: Boolean,
    val useEdge: Boolean
) extends Serializable {

  def this() = this(true, true, true)

}

object TripletFields {
  val None = new TripletFields(false, false, false)
  val SrcWithEdge = new TripletFields(true, false, true)
  val SrcWithoutEdge = new TripletFields(true, false, false)
  val DstWithEdge = new TripletFields(false, true, true)
  val DstWithoutEdge = new TripletFields(false, true, false)
  val BothSidesWithEdge = new TripletFields(true, true, true)
  val BothSidesWithoutEdge = new TripletFields(true, true, false)
}