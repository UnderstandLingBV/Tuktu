package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.nlp.models.ShortTextClassifier
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

class ShortTextClassifierTrainProcessor(resultName: String) extends BaseMLTrainProcessor[ShortTextClassifier](resultName) {
    var tokensField: String = _
    var labelField: String = _
    var minCount: Int = _
    var n: Int = _
    var C: Double = _
    var eps: Double = _
    
    override def initialize(config: JsObject) {
        tokensField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        minCount = (config \ "min_count").as[Int]
        n = (config \ "n").as[Int]
        C = (config \ "C").as[Double]
        eps = (config \ "epsilon").as[Double]
        
        super.initialize(config)
    }
    
    override def instantiate(): ShortTextClassifier =
        new ShortTextClassifier(n, minCount)
    
    override def train(data: List[Map[String, Any]], model: ShortTextClassifier): ShortTextClassifier = {
        // Add the documents
        val x = collection.mutable.ListBuffer.empty[List[String]]
        val y = collection.mutable.ListBuffer.empty[Double]
        data.map {datum =>
            x += datum(tokensField).asInstanceOf[String].split(" ").toList
            y += (datum(labelField) match {
                case l: Int => l.toDouble
                case l: String => l.toDouble
                case l: Double => l
                case _ => datum(labelField).toString.toDouble
            })
        }
        
        // Train
        model.trainClassifier(x.toList, y.toList, C, eps)
        
        model
    }
}

class ShortTextClassifierApplyProcessor(resultName: String) extends BaseMLApplyProcessor[ShortTextClassifier](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: ShortTextClassifier): List[Map[String, Any]] = {
        data.map {datum =>
            datum + (resultName -> {
                model.predict(datum(dataField) match {
                    case s: Seq[String] => s.toList
                    case s: Array[String] => s.toList
                    case s: List[String] => s
                    case s: String => s.split(" ").toList
                    case _ => datum(dataField).toString.split(" ").toList
                })
            }) 
        }
    }
}

class ShortTextClassifierDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[ShortTextClassifier](resultName) {
    var minCount: Int = _
    var n: Int = _
    
    override def initialize(config: JsObject) {
        minCount = (config \ "min_count").as[Int]
        n = (config \ "n").as[Int]
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new ShortTextClassifier(n, minCount)
        model.deserialize(filename)
        model
    }
}