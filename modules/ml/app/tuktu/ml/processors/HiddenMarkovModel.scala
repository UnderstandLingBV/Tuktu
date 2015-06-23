package tuktu.ml.processors

import play.api.libs.json.JsObject
import tuktu.ml.models.hmm.BaumWelchMethod
import tuktu.ml.models.hmm.HMM
import tuktu.ml.models.hmm.HiddenMarkovModel
import tuktu.ml.models.hmm.ViterbiAlgorithm

class HiddenMarkovModelTrainer(resultName: String) extends BaseMLTrainProcessor[HiddenMarkovModel](resultName) {
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
    override def instantiate(): HiddenMarkovModel =
        new HiddenMarkovModel(numHidden, numObservable)
        
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

class HiddenMarkovModelApply(resultName: String) extends BaseMLApplyProcessor[HiddenMarkovModel](resultName) {
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