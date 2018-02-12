package tuktu.nlp.processors

import tuktu.api.utils
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLApplyProcessor
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.nlp.models.ShortTextClassifier
import de.bwaldvogel.liblinear.FeatureNode
import tuktu.nlp.models.NBSVM
import play.api.libs.json.Json

class NBSVMTrainProcessor(resultName: String) extends BaseMLTrainProcessor[NBSVM](resultName) {
    var tokensField: String = _
    var labelField: String = _
    var epochs: Int = _
    var lambda: Double = _
    var alpha: Double = _
    
    override def initialize(config: JsObject) {
        tokensField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        epochs = (config \ "epochs").as[Int]
        lambda = (config \ "lambda").as[Double]
        alpha = (config \ "alpha").as[Double]

        super.initialize(config)
    }
    
    override def instantiate(data: List[Map[String, Any]]): NBSVM = new NBSVM
    
    override def train(data: List[Map[String, Any]], model: NBSVM): NBSVM = {
        val texts = data.map {datum =>
            datum(tokensField) match {
                case a: Seq[String] => a.toList
                case a: Any => a.toString.split(" ").toList
            }
        }
        val labels = data.map {datum =>
            datum(labelField).toString.toInt
        }
        model.train(epochs, lambda, alpha, texts, labels)
        model
    }
}

class NBSVMApplyProcessor(resultName: String) extends BaseMLApplyProcessor[NBSVM](resultName) {
    var dataField: String = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: NBSVM): List[Map[String, Any]] = {
        data.map {datum =>
            datum + (resultName -> model({
                datum(dataField) match {
                    case a: Seq[String] => a.toList
                    case a: Any => a.toString.split(" ").toList
                }
            }))
        }
    }
}

class NBSVMDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[NBSVM](resultName) {
    override def deserializeModel(filename: String) = {
        val model = new NBSVM
        model.deserialize(filename)
        model
    }
}