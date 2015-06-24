package tuktu.ml.processors

import play.api.libs.json.JsObject
import tuktu.ml.models.hmm.BaumWelchMethod
import tuktu.ml.models.hmm.HiddenMarkovModel
import tuktu.ml.models.hmm.ViterbiAlgorithm
import tuktu.ml.models.hmm.PredictAlgorithm

/**
 * Trains a hidden markov model
 */
class HMMTrainProcessor(resultName: String) extends BaseMLTrainProcessor[HiddenMarkovModel](resultName) {
    // From which field to we extract the observations
    var observationsField = ""

    // Initialization params
    var numHidden = 0
    var numObservable = 0

    // Keep track of how many packets we have seen
    var packetCount = 0

    override def initialize(config: JsObject) {
        observationsField = (config \ "observations_field").as[String]

        // Get number of hidden and observable states
        numHidden = (config \ "num_hidden").as[Int]
        numObservable = (config \ "num_observable").as[Int]

        super.initialize(config)
    }

    // Instantiates a Hidden Markov Model with a number of hidden states and a number of observable states
    override def instantiate(): HiddenMarkovModel = {
        val model = new HiddenMarkovModel(numHidden, numObservable) {
            // Initialize A, B, Pi
            for {
                i <- 0 to numHidden - 1
                j <- 0 to numHidden - 1
            } A(i, j) = 1.0 / numHidden
            for {
                i <- 0 to numHidden - 1
                j <- 0 to numObservable - 1
            } B(i, j) = 1.0 / numObservable
            for (i <- 0 to numHidden - 1) Pi(i) = 1.0 / numHidden
        }
        
        // Use the results from the paper
        model.A(0,0) = 0.083333
        model.A(0, 1) = 0.083333
        model.A(0, 2) = 0.583333
        model.A(0, 3) = 0.083333
        model.A(0, 4) = 0.083333
        model.A(0, 5) = 0.083333
        
        model.A(1,0) = 0.083333
        model.A(1, 1) = 0.083333
        model.A(1, 2) = 0.333333
        model.A(1, 3) = 0.333333
        model.A(1, 4) = 0.083333
        model.A(1, 5) = 0.083333
        
        model.A(2,0) = 0.083333
        model.A(2, 1) = 0.154833
        model.A(2, 2) = 0.154833
        model.A(2, 3) = 0.083333
        model.A(2, 4) = 0.368833
        model.A(2, 5) = 0.154833
        
        model.A(3,0) = 0.333333
        model.A(3, 1) = 0.083333
        model.A(3, 2) = 0.333333
        model.A(3, 3) = 0.083333
        model.A(3, 4) = 0.083333
        model.A(3, 5) = 0.083333
        
        model.A(4,0) = 0.208333
        model.A(4, 1) = 0.208333
        model.A(4, 2) = 0.333333
        model.A(4, 3) = 0.083333
        model.A(4, 4) = 0.083333
        model.A(4, 5) = 0.083333
        
        model.A(5,0) = 0.083333
        model.A(5, 1) = 0.083333
        model.A(5, 2) = 0.083333
        model.A(5, 3) = 0.333333
        model.A(5, 4) = 0.083333
        model.A(5, 5) = 0.333333
        
        model.B(0,1) = 0.02
        model.B(0,0) = 0.98
        model.B(1,1) = 0.5
        model.B(1,0) = 0.5
        model.B(2,1) = 0.71
        model.B(2,0) = 0.29
        model.B(3,1) = 0.02
        model.B(3,0) = 0.98
        model.B(4,1) = 0.02
        model.B(4,0) = 0.98
        model.B(5,1) = 0.98
        model.B(5,0) = 0.02
        
        model.Pi(0) = 0.06
        model.Pi(1) = 0.11
        model.Pi(2) = 0.39
        model.Pi(3) = 0.11
        model.Pi(4) = 0.22
        model.Pi(5) = 0.11
        
        model
    }
        
    // Trains the Hidden Markov Model using a sequence of observations for a number of steps
    override def train(data: List[Map[String, Any]], model: HiddenMarkovModel): HiddenMarkovModel = {
        val observations = (for (datum <- data) yield {
            // Get the observations, as sequence
            datum(observationsField).asInstanceOf[Seq[Int]]
        }).groupBy(elem => elem).mapValues(value => value.size)
        
        // Further train the HMM
        val method = new BaumWelchMethod(observations)
        val newModel = method.apply(model)
        
        newModel
    }
}

/**
 * Runs the Viterbi algorithm using a given HMM to decode. This will yield the most probable
 * hidden state sequence for a given observable state sequence
 */
class HMMApplyDecodeProcessor(resultName: String) extends BaseMLApplyProcessor[HiddenMarkovModel](resultName) {
    // From which field to we extract the observations
    var observationsField = ""

    override def initialize(config: JsObject) {
        observationsField = (config \ "observations_field").as[String]

        super.initialize(config)
    }

    // Apply the HMM using the Viterbi algorithm to all our data points
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: HiddenMarkovModel): List[Map[String, Any]] = {
        for (datum <- data) yield {
            // Apply viterbi algorithm
            val observations = datum(observationsField).asInstanceOf[Seq[Int]]
            val viterbi = new ViterbiAlgorithm(observations)(model)
            val result = viterbi(observations.size, observations.last)

            datum + (resultName -> Map(
                "delta" -> result._1,
                "sequence" -> result._2))
        }
    }
}

/**
 * Predicts the most likely sequence of future steps given a hidden markov model
 */
class HMMApplyPredictProcessor(resultName: String) extends BaseMLApplyProcessor[HiddenMarkovModel](resultName) {
    // From which field to we extract the observations
    var steps: Int = _

    override def initialize(config: JsObject) {
        steps = (config \ "steps").as[Int]

        super.initialize(config)
    }

    // Apply the HMM using the Viterbi algorithm to all our data points
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: HiddenMarkovModel): List[Map[String, Any]] = {
        for (datum <- data) yield {
            // Apply prediction algorithm
            val sequence = PredictAlgorithm.predict(model, steps)

            datum + (resultName -> sequence)
        }
    }
}