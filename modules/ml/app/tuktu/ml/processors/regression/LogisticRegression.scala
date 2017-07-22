package tuktu.ml.processors.regression

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.ml.models.hmm.HiddenMarkovModel
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.ml.models.regression.LogisticRegression
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLApplyProcessor

/**
 * Trains a logistic regression classifier
 */
class LogisticRegressionTrainProcessor(resultName: String) extends BaseMLTrainProcessor[LogisticRegression](resultName) {
    var dataField = ""
    var labelField = ""
    
    var lambda: Double = _
    var tolerance: Double = _
    var maxIterations: Int = _
    
    override def initialize(config: JsObject) {
        // Get parameters
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        
        lambda = (config \ "lambda").asOpt[Double].getOrElse(0.0)
        tolerance = (config \ "tolerance").asOpt[Double].getOrElse(1E-5)
        maxIterations = (config \ "max_iterations").asOpt[Int].getOrElse(500)

        super.initialize(config)
    }

    override def instantiate(data: List[Map[String, Any]]): LogisticRegression =
        new LogisticRegression(lambda, tolerance, maxIterations)
    
    // Trains the logistic regression classifier
    override def train(data: List[Map[String, Any]], model: LogisticRegression): LogisticRegression = {
        for (datum <- data) yield {
            // Get the data
            val data = datum(dataField).asInstanceOf[Seq[Double]].toArray
            val label = datum(labelField) match {
                case a: Int => a
                case a: Double => a.toInt
                case a: Long => a.toInt
                case a: Any => a.toString.toInt
            }
            
            // Add it
            model.addData(Array(data), Array(label))
        }

        model.train
        
        model
    }
}

/**
 * Applies a logistic regression model to classify data
 */
class LogisticRegressionApplyProcessor(resultName: String) extends BaseMLApplyProcessor[LogisticRegression](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: LogisticRegression): List[Map[String, Any]] = {
        for (datum <- data) yield
            datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]].toArray))
    }
}

/**
 * Deserializes a logistic regression model
 */
class LogisticRegressionDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[LogisticRegression](resultName) {
    var lambda: Double = _
    var tolerance: Double = _
    var maxIterations: Int = _
    
    override def initialize(config: JsObject) {
        // Get parameters
        lambda = (config \ "lambda").asOpt[Double].getOrElse(0.0)
        tolerance = (config \ "tolerance").asOpt[Double].getOrElse(1E-5)
        maxIterations = (config \ "max_iterations").asOpt[Int].getOrElse(500)

        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new LogisticRegression(lambda, tolerance, maxIterations)
        model.deserialize(filename)
        model
    }
}