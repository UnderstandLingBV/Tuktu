package tuktu.ml.processors.preprocessing

import tuktu.ml.processors.BaseMLTrainProcessor
import play.api.libs.json.JsObject
import tuktu.ml.models.preprocessing.Normalization
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * 'Trains' a normalization model by keeping track of minimums/maximums per key
 */
class NormalizationTrainProcessor(resultName: String) extends BaseMLTrainProcessor[Normalization](resultName) {
    var min = 0.0
    var max = 1.0
    var fields: Option[List[String]] = _
    
    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]]
        min = (config \ "min").asOpt[Double].getOrElse(0.0)
        max = (config \ "max").asOpt[Double].getOrElse(1.0)
        super.initialize(config)
    }
    
    override def instantiate(): Normalization =
        new Normalization(min, max)
    
    override def train(data: List[Map[String, Any]], model: Normalization): Normalization = {
        for (datum <- data) yield {
            // Get key/value pairs we need to normalize and run the normalization
            datum.foreach(el => fields match {
                case Some(fs) => if (fs.contains(el._1)) model.addData(el._1, model.toDouble(el._1, el._2))
                case None => model.addData(el._1, model.toDouble(el._1, el._2))
            })
        }
         
        model
    }
}

/**
 * Normalizes data
 */
class NormalizationApplyProcessor(resultName: String) extends BaseMLApplyProcessor[Normalization](resultName) {
    var fields: Option[List[String]] = _
    
    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]]
        
        super.initialize(config)
    }
    
    // Apply normalization
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Normalization): List[Map[String, Any]] = {
        for (datum <- data) yield {
            datum.map(elem => fields match {
                case Some(fs) => if (fs.contains(elem._1)) elem._1 -> model.normalize(elem._1, model.toDouble(elem._1, elem._2)) else elem
                case None => elem._1 -> model.normalize(elem._1, model.toDouble(elem._1,elem._2))
            })
        }
    }
}

/**
 * Deserializes a normalization model
 */
class NormalizationDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[Normalization](resultName) {
    var min = 0.0
    var max = 1.0
    
    override def initialize(config: JsObject) {
        min = (config \ "min").asOpt[Double].getOrElse(0.0)
        max = (config \ "max").asOpt[Double].getOrElse(1.0)
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new Normalization(min, max)
        model.deserialize(filename)
        model
    }
}