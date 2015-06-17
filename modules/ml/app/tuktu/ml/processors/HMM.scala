package tuktu.ml.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.ml.models.hmm.HiddenMarkovModel
import tuktu.ml.models.hmm.HMM

/**
 * Trains a hidden markov model using observations
 */
class HMMTrainer(resultName: String) extends BaseProcessor(resultName) {
    // From which field to we extract the observations
    var observationsField = ""
    // Get the name of the HMM to store in cache
    var modelName = ""
    // How many steps to execute while training?
    var steps = 5
    // Destory the model on EOF?
    var destroyEOF = true
    
    // Keep track of how many packets we have seen
    var packetCount = 0
    
    // Our hidden markov model
    var hmm: HMM = _
    
    override def initialize(config: JsObject) = {
        observationsField = (config \ "observations_field").as[String]
        modelName = (config \ "model_name").as[String]
        steps = (config \ "steps").asOpt[Int].getOrElse(5)
        destroyEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
        
        // Get number of hidden and observable states
        val numHidden = (config \ "num_hidden").as[Int]
        val numObservable = (config \ "num_observable").as[Int]
        
        // @TODO: Get the HMM from our model repository
        hmm = new HMM(numHidden, numObservable)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.data.foreach(datum => {
            // Should we add the model?
            val modelNameEval = utils.evaluateTuktuString(modelName, datum)
            
            // Get the observations, as sequence
            val observations = datum(observationsField).asInstanceOf[Seq[Int]].toList
            
            // Further train the HMM
            hmm.TrainBaumWelch(observations, steps)
        })
        
        data
    }) compose Enumeratee.onEOF(() => destroyEOF match {
        case true => {
            // @TODO: Send model repository the signal to clean up the model
        }
        case _ => {}
    })
}

/**
 * Applies a hidden markov model to new sequences to find the most likely emission
 */
class HMMApply(resultName: String) extends BaseProcessor(resultName) {
    var hmmField = ""
    
    override def initialize(config: JsObject) = {
        hmmField = (config \ "hmm_field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        data
    })
}