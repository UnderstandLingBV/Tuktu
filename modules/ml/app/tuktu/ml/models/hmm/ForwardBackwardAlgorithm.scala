package tuktu.ml.models.hmm

import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

/**
 * Runs the forward-backward algorithm to update transition and emission probabilities
 * 
 * Implementation from https://github.com/balshor/shimm
 */
class ForwardBackwardAlgorithm(val observations: Seq[Int])(implicit val model: HiddenMarkovModel) {
    import model._
    import scala.collection.mutable.{ Map, HashMap }

    private val alphaCache: Map[(Int, Int), Double] = new HashMap
    private val betaCache: Map[(Int, Int), Double] = new HashMap

    /*
   * What is the probability of having seen observations 0 through (t-1) and ending up at state j at time (t-1)? 
   */
    def alpha(t: Int, j: Int): Double = {
        val key = (t, j)
        if (alphaCache contains key) {
            alphaCache(key)
        } else {
            val result = if (t > 1) {
                ((0 to numberOfStates - 1) map { i =>
                    alpha(t - 1, i) * A(i, j)
                } sum) * B(j, observations(t - 1))
            } else {
                Pi(j) * B(j, observations.head)
            }
            alphaCache(key) = result
            result
        }
    }

    /* 
   * Having seen observations 0 through (t-1) and knowing that the current state is i, what is the probability of seeing 
   * the observations at times t and greater? 
   */
    def beta(t: Int, i: Int): Double = {
        val key = (t, i)
        if (betaCache contains key) {
            betaCache(key)
        } else {
            val result = if (t < observations.length) {
                (0 to numberOfStates - 1) map { j =>
                    A(i, j) * B(j, observations(t)) * beta(t + 1, j)
                } sum
            } else {
                1.0
            }
            betaCache(key) = result
            result
        }
    }

    /*
   * Given all observations, what is the probability of being in state i at time (t-1)?
   */
    def gamma(t: Int, i: Int): Double = {
        def f(j: Int) = alpha(t, j) * beta(t, j)

        val numerator = f(i)
        val denominator = (0 to numberOfStates - 1) map (f) sum

        numerator / denominator
    }

    /*
   * Given all observations, what is the probability of being in state i at time (t-1) and state j at time t?
   */
    def xi(t: Int, i: Int, j: Int): Double = {
        def f(x: Int, y: Int) = alpha(t, x) * A(x, y) * B(y, observations(t)) * beta(t + 1, y)

        val numerator = f(i, j)
        val denominator = (0 to numberOfStates - 1) flatMap { x =>
            (0 to numberOfStates - 1) map { y =>
                f(x, y)
            }
        } sum

        numerator / denominator
    }
}