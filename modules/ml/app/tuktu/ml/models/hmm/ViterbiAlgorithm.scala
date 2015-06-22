package tuktu.ml.models.hmm

import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

class ViterbiAlgorithm(val observations: Seq[Int])(implicit val model: HiddenMarkovModel) {
    import model._
    import scala.collection.mutable.{ Map, HashMap, ArrayBuffer }

    private val cache: Map[(Int, Int), (Double, Seq[Int])] = new HashMap

    /** Computes (delta_t(i), psi_t(i)) */
    def apply(t: Int, i: Int): (Double, Seq[Int]) = {

        implicit val cmp = new Ordering[(Double, Seq[Int])] {
            def compare(x: (Double, Seq[Int]), y: (Double, Seq[Int])): Int = {
                java.lang.Double.compare(x._1, y._1)
            }
        }

        if (cache contains (t, i)) {
            cache((t, i))
        } else {
            val result: (Double, Seq[Int]) = if (t == 1) {
                (Pi(i) * B(i, observations(t - 1)), Seq(i))
            } else {
                (0 to numberOfStates - 1) map { j =>
                    val previousResult: (Double, Seq[Int]) = apply(t - 1, j)
                    (previousResult._1 * A(j, i) * B(i, observations(t - 1)), previousResult._2 :+ j)
                } max
            }
            cache((t, i)) = result
            result
        }
    }

}