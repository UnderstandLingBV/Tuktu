package tuktu.ml.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.ml.models.BaseModel
import tuktu.ml.models.hmm.HMM

/**
 * Trains a hidden markov model using observations
 */
class HMMTrainer(resultName: String) extends BaseMLTrainProcessor[HMM](resultName) {
    // From which field to we extract the observations
    var observationsField = ""
    // How many steps to execute while training?
    var steps = 5

    // Initialization params
    var numHidden = 0
    var numObservable = 0

    // Keep track of how many packets we have seen
    var packetCount = 0

    override def initialize(config: JsObject) {
        observationsField = (config \ "observations_field").as[String]
        steps = (config \ "steps").asOpt[Int].getOrElse(5)

        // Get number of hidden and observable states
        numHidden = (config \ "num_hidden").as[Int]
        numObservable = (config \ "num_observable").as[Int]

        super.initialize(config)
    }

    // Instantiates a Hidden Markov Model with a number of hidden states and a number of observable states
    override def instantiate(): HMM =
        new HMM(numHidden, numObservable)

    // Trains the Hidden Markov Model using a sequence of observations for a number of steps
    override def train(data: List[Map[String, Any]], model: HMM): HMM = {
        data.foreach(datum => {
            // Get the observations, as sequence
            val observations = datum(observationsField).asInstanceOf[Seq[Int]].toList

            // Further train the HMM
            model.TrainBaumWelch(observations, steps)
        })

        model
    }
}

/**
 * Applies a hidden markov model to new sequences to find the most likely emission
 */
class HMMApply(resultName: String) extends BaseMLApplyProcessor[HMM](resultName) {
    // From which field to we extract the observations
    var observationsField = ""

    override def initialize(config: JsObject) {
        observationsField = (config \ "observations_field").as[String]

        super.initialize(config)
    }

    // Apply the HMM using the Viterbi algorithm to all our data points
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: HMM): List[Map[String, Any]] = {
        for (datum <- data) yield {
            // Apply viterbi algorithm
            val observations = datum(observationsField).asInstanceOf[Seq[Int]]
            val viterbi = new model.Viterbi(observations)
            val result = viterbi(observations.size, observations.last)

            datum + (resultName -> Map(
                "delta" -> result._1,
                "sequence" -> result._2))
        }
    }
}