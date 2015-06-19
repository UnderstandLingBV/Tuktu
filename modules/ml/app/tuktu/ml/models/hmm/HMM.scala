package tuktu.ml.models.hmm

import java.util.HashMap
import tuktu.ml.models.BaseModel

/**
 * Implements a Hidden Markov Model (HMM)
 * @param numHidden The number of hidden states
 * @param numObserved The number of observed states
 */
class HMM(numHidden: Int, numObserved: Int) extends BaseModel() {
    // Initialize matrices
    
    // State probabilities
    var pi = collection.mutable.ListBuffer[Double]()
    for (i <- 0 to numHidden - 1) pi += 1.0 / numHidden
    
    // Transition probabilities
    var A = collection.mutable.ListBuffer[collection.mutable.ListBuffer[Double]]() 
    for (i <- 0 to numHidden - 1) {
        A(i) = collection.mutable.ListBuffer[Double]()
        for (j <- 0 to numHidden - 1) A(i) += 1.0 / numHidden
    }
    // Emission probabilities
    var B = collection.mutable.ListBuffer[collection.mutable.ListBuffer[Double]]()
    for (i <- 0 to numHidden - 1) {
        B(i) = collection.mutable.ListBuffer[Double]()
        for (j <- 0 to numObserved - 1) B(i) += 1.0 / numObserved
    }
    
    /**
     * Applies the Baum-Welch algorithm for training the HMM
     * @param observations The observed states, in-order
     * @param steps The number of steps
     */
    def TrainBaumWelch(observations: List[Int], steps: Int) = {
        var piNew = pi
        var ANew = A
        var BNew = B
            
        // For each step, we do a forward-backward step
        for (iteration <- 0 to steps - 1) {
            // Run forward step and backward step
            val forward = RunForward(observations)
            val backward = RunBackward(observations)
            
            // Update the initial state probabilities
            for (i <- 0 to numHidden - 1)
                piNew(i) = ComputeGamma(i, 0, observations, forward, backward)

            // Update transition matrix
            for (i <- 0 to numHidden - 1) {
                for (j <- 0 to numHidden - 1) {
                    var numerator = 0.0
                    var denominator = 0.0
                    
                    // Go over time series
                    for (t <- 0 to observations.size - 1) {
                        numerator += ComputeProb(t, i, j, observations, forward, backward)
                        denominator += ComputeGamma(i, t, observations, forward, backward)
                    }
                    
                    // Update
                    ANew(i)(j) = if (numerator == 0.0) 0.0 else numerator / denominator
                }
            }
            
            // Update emission matrix
            for (i <- 0 to numHidden - 1) {
                for (k <- 0 to numObserved - 1) {
                    var numerator = 0.0
                    var denominator = 0.0
                    
                    // Go over the time series
                    for (t <- 0 to observations.size - 1) {
                        val gamma = ComputeGamma(i, t, observations, forward, backward)
                        numerator += {if (k == observations(t)) gamma else 0.0}
                        denominator += gamma
                    }
                    
                    // Update
                    BNew(i)(k) = if (numerator == 0.0) 0.0 else numerator / denominator
                }
            }
            
            // Update with new matrices
            pi = piNew
            A = ANew
            B = BNew
        }
    }
    
    /**
     * Runs the Viterbi algorithm to find the most likely emission after a sequence of observations
     */
    class Viterbi(observations: Seq[Int]) {
        val cache = collection.mutable.Map[(Int, Int), (Double, Seq[Int])]()

        /**
         * Computes (delta_t(i), psi_t(i))
         */
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
                    (pi(i) * B(i)(observations(t - 1)), Seq(i))
                } else {
                    (0 to numHidden - 1) map { j =>
                        val previousResult: (Double, Seq[Int]) = apply(t - 1, j)
                        (previousResult._1 * A(j)(i) * B(i)(observations(t - 1)), previousResult._2 :+ j)
                    } max
                }
                cache((t, i)) = result
                result
            }
        }
    }
    
    /**
     * Performs the forward step of the algorithm
     */
    private def RunForward(observations: List[Int]) = {
        var probs = collection.mutable.ListBuffer[collection.mutable.ListBuffer[Double]]()
        
        // Base-case, initialize for time 0
        for (i <- 0 to numHidden - 1) {
            probs(i) += (pi(i) * B(i)(observations(0)))
            for (j <- 1 to observations.size - 1) probs(i) += 0.0
        }
        
        // Induction step, update state probabilities
        for (time <- 0 to observations.size - 2) {
            for (j <- 0 to numHidden - 1) {
                // Init time offset
                probs(j)(time + 1) = 0.0
                
                // Update temp transition matrix
                for (i <- 0 to numHidden - 1)
                    probs(j)(time + 1) = probs(j)(time + 1) + probs(i)(time) * A(i)(j)
                    
                // Emission matrix
                probs(j)(time + 1) = probs(j)(time + 1) * B(j)(observations(time + 1))
            }
        }
        
        probs.toList.map(_.toList)
    }
    
    /**
     * Performs the backward step of the algorithm
     */
    private def RunBackward(observations: List[Int]) = {
        var probs = collection.mutable.ListBuffer[collection.mutable.ListBuffer[Double]]()
        
        // Base-case, initialize for time T-1
        for (i <- 0 to numHidden - 1) {
            for (j <- 0 to observations.size - 2) probs(i) += 0.0
            probs(i) += 1.0
        }
        
        // Induction step, update state probabilities
        for (time <- observations.size - 2 to 0 by -1) {
            for (i <- 0 to numHidden - 1) {
                // Init time offset
                probs(i)(time) = 0.0
                
                // Step
                for (j <- 0 to numHidden - 1)
                    probs(i)(time) = probs(i)(time) + probs(j)(time + 1) * A(i)(j) + B(j)(observations(time + 1))
            }
        }
        
        probs.toList.map(_.toList)
    }
    
    /**
     * Computes the gamma function of i and t
     */
    private def ComputeGamma(i: Int, t: Int, observations: List[Int], fwd: List[List[Double]], bwd: List[List[Double]]) = {
        val numerator = fwd(i)(t) * bwd(i)(t)
        var denominator = 0.0
        
        // Compute denominator
        for (j <- 0 to numHidden - 1) denominator += fwd(j)(t) * bwd(j)(t)
        
        // Return gamma
        if (numerator == 0.0) 0.0 else numerator / denominator
    }
    
    /**
     * Computes the probability P(X_t = s_i, X_(t+1) = s_j | observations)
     */
    private def ComputeProb(t: Int, i: Int, j: Int, observations: List[Int], fwd: List[List[Double]], bwd: List[List[Double]]) = {
        val numerator = if (t == observations.size - 1) fwd(i)(t) * A(i)(j)
            else fwd(i)(t) * A(i)(j) * B(j)(observations(t + 1)) * bwd(j)(t + 1)
        
        var denominator = 0.0
        for (k <- 0 to numHidden - 1)
            denominator += fwd(k)(t) * bwd(k)(t)
            
        // Return probability
        if (numerator == 0.0) 0.0 else numerator / denominator
    }
    
    def saveModel(location: String) = {
        
    }
    
    def loadModel(location: String) = {
        
    }
}