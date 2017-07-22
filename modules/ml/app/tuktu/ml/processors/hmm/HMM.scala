package tuktu.ml.processors.hmm

import play.api.libs.json.JsObject
import tuktu.ml.models.hmm._
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

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

    // Used for setting priors
    var priorA: List[List[Double]] = _
    var priorB: List[List[Double]] = _
    var priorPi: List[Double] = _
    var priorsGiven = false

    override def initialize(config: JsObject) {
        observationsField = (config \ "observations_field").as[String]

        // Get number of hidden and observable states
        numHidden = (config \ "num_hidden").as[Int]
        numObservable = (config \ "num_observable").as[Int]

        // Read out the priors, if any
        val priorsObject = (config \ "priors").asOpt[JsObject]
        priorsObject match {
            case Some(priors) => {
                priorA = (priors \ "transitions").as[List[List[Double]]]
                priorB = (priors \ "emissions").as[List[List[Double]]]
                priorPi = (priors \ "start").as[List[Double]]
                priorsGiven = true
            }
            case None => {}
        }

        super.initialize(config)
    }

    // Instantiates a Hidden Markov Model with a number of hidden states and a number of observable states
    override def instantiate(data: List[Map[String, Any]]): HiddenMarkovModel = {
        new HiddenMarkovModel(numHidden, numObservable) {
            // Set priors if any
            if (priorsGiven) {
                // Loop and initialize the matrices
                for {
                    i <- 0 to numHidden - 1
                    j <- 0 to numHidden - 1
                } A(i, j) = priorA(i)(j)
                for {
                    i <- 0 to numHidden - 1
                    j <- 0 to numObservable - 1
                } B(i, j) = priorB(i)(j)
                for (i <- 0 to numHidden - 1) Pi(i) = priorPi(i)

                this.normalize()
            }
            else {
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
        }
    }

    // Trains the Hidden Markov Model using a sequence of observations for a number of steps
    override def train(data: List[Map[String, Any]], model: HiddenMarkovModel): HiddenMarkovModel = {
        val observations = (for (datum <- data if datum.contains(observationsField)) yield {
            // Get the observations, as sequence
            datum(observationsField).asInstanceOf[Seq[Int]]
        }).groupBy(elem => elem).mapValues(value => value.size)

        if (observations.nonEmpty) {
            // Further train the HMM
            val method = new BaumWelchMethod(observations)
            val newModel = method.apply(model)

            newModel
        } else {
            model
        }
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

/**
 * Deserializes a hidden markov model
 */
class HMMDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[HiddenMarkovModel](resultName) {
    var numHidden = 0
    var numObservable = 0

    override def initialize(config: JsObject) {
        // Get number of hidden and observable states
        numHidden = (config \ "num_hidden").as[Int]
        numObservable = (config \ "num_observable").as[Int]

        super.initialize(config)
    }

    override def deserializeModel(filename: String) = {
        val model = new HiddenMarkovModel(numHidden, numObservable)
        model.deserialize(filename)
        model
    }
}