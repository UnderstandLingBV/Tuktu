package tuktu.ml.processors

/**
 * Implements a Hidden Markov Model (HMM)
 * @param numHidden The number of hidden states
 * @param numObserved The number of observed states
 */
class HMM(numHidden: Int, numObserved: Int) {
    // Initialize matrices
    
    // State probabilities
    var pi = for (i <- 0 to numHidden - 1) yield 0.0
    // Transition probabilities
    var A = for (i <- 0 to numHidden - 1) yield
        for (j <- 0 to numHidden - 1) yield 0.0
    // Emission probabilities
    var B = for (i <- 0 to numHidden - 1) yield
        for (j <- 0 to numObserved - 1) yield 0.0
    
    /**
     * Applies the Baum-Welch algorithm for training the HMM
     * @param observations The observed states, in-order
     * @param steps The number of steps
     */
    def TrainBaumWelch(observations: List[Int], steps: Int) = {
        // For each step, we do a forward-backward step
        for (iteration <- 0 to steps - 1) {
            // Run forward step and backward step
            val forward = RunForward(observations)
            val backward = RunBackward(observations)
            
            // Update the state probabilities
            
        }
    }
    
    private def RunForward(observations: List[Int]) = {
        // Base-case, initialize for time 0
        val probs = for (i <- 0 to numHidden - 1) yield 
            pi(i) * B(i)(observations(0))
    }
    
    private def RunBackward(observations: List[Int]) = {
        
    }
    
}