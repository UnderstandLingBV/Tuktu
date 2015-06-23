package tuktu.ml.models.hmm

import java.util.Random

/**
 * Predicts the most probable sequence of steps an HMM would generate
 */
object PredictAlgorithm {
    def predict(model: HiddenMarkovModel, steps: Int) = {
        val rand = new Random
        
        // Determine initial hidden state based on Pi
        var hiddenState = {
            val randNr = rand.nextDouble
            var state = 0
            var cumul = 0.0
            while (cumul + model.Pi(state) < randNr) {
                cumul += model.Pi(state)
                state += 1
            }
            
            state
        }

        // Now draw output steps for as long as we have steps, using the current distributions
        (for (step <- 0 to steps -1) yield {
            // Choose the most probable output state given current hidden state
            val outputState = {
                val randNr = rand.nextDouble
                var state = 0
                var cumul = 0.0
                while (cumul + model.B(hiddenState, state) < randNr) {
                    cumul += model.B(hiddenState, state)
                    state += 1
                }
                
                state
            }
          
            // Determine next hidden state
            hiddenState =  {
                val randNr = rand.nextDouble
                var state = 0
                var cumul = 0.0
                while (cumul + model.A(hiddenState, state) < randNr) {
                    cumul += model.A(hiddenState, state)
                    state += 1
                }
                
                state
            }
          
            // Return the output state
            outputState
        }).toList
    }
}