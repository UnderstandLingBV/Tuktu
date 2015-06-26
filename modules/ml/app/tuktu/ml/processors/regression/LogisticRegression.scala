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
    var learnRate = 0
    var nIterations = 0
    var batchSize = 0
    var dataField = ""
    var labelField = ""
    
    override def initialize(config: JsObject) {
        // Get parameters
        learnRate = (config \ "learn_rate").as[Int]
        nIterations = (config \ "num_iterations").as[Int]
        batchSize = (config \ "batch_size").asOpt[Int].getOrElse(10)
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]

        super.initialize(config)
    }

    override def instantiate(): LogisticRegression =
        new LogisticRegression(learnRate, nIterations)
    
    // Trains the logistic regression classifier
    override def train(data: List[Map[String, Any]], model: LogisticRegression): LogisticRegression = {
        val records = {
            val res = for (datum <- data) yield {
                // Get the data
                (datum(dataField).asInstanceOf[Seq[Int]].toArray,
                        datum(labelField).asInstanceOf[Int])
            }
            // Batched or not?
            if (batchSize == 0) Iterator(res)
            else res.grouped(batchSize)
        }
        
        // Further train the regression model
        records.foreach(record => {
            // Split data and labels
            val trainData = record.map(_._1).toArray
            val labels = record.map(_._2).toArray
            model.train(trainData, labels, false)
        })
        
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
            datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Int]].toArray))
    }
}

/**
 * Deserializes a logistic regression model
 */
class LogisticRegressionDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[LogisticRegression](resultName) {
    var learnRate = 0
    var nIterations = 0
    
    override def initialize(config: JsObject) {
        // Get learn rate and iteration count
        learnRate = (config \ "learn_rate").as[Int]
        nIterations = (config \ "num_iterations").as[Int]

        super.initialize(config)
    }
    
    override def deserializeModel() = {
        val model = new LogisticRegression(learnRate, nIterations)
        model.deserialize(fileName)
        model
    }
}