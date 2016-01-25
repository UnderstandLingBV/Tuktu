package tuktu.ml.processors.svm

import play.api.libs.json.JsObject
import tuktu.ml.models.svm.SupportVectorMachine
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * Trains a support vector machines classifier
 */
class SVMTrainProcessor(resultName: String) extends BaseMLTrainProcessor[SupportVectorMachine](resultName) {
    var dataField = ""
    var labelField = ""
    var kernel: String = _
    var penalty: Double = _
    var strategy: String = _
    var kernelParams: List[Double] = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        kernel = (config \ "kernel").asOpt[String].getOrElse("linear")
        kernelParams = (config \ "kernel_params").asOpt[List[Double]].getOrElse(List())
        penalty = (config \ "penalty").asOpt[Double].getOrElse(2.0)
        strategy = (config \ "strategy").asOpt[String].getOrElse("one_vs_all")
        
        super.initialize(config)
    }
    
    override def instantiate(): SupportVectorMachine =
        new SupportVectorMachine
        
    // Trains the linear regression classifier
    override def train(data: List[Map[String, Any]], model: SupportVectorMachine): SupportVectorMachine = {
        val records = for (datum <- data) yield {
            // Get the data
            (datum(dataField).asInstanceOf[Seq[Double]].toArray,
                    datum(labelField).asInstanceOf[Int])
        }
        
        // Train the regression model
        val trainData = records.map(_._1).toArray
        val labels = records.map(_._2).toArray
        model.train(trainData, labels, penalty, strategy, kernel, kernelParams)

        model
    }
}

/**
 * Applies a support vector machine model to predict a y-value
 */
class SVMApplyProcessor(resultName: String) extends BaseMLApplyProcessor[SupportVectorMachine](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: SupportVectorMachine): List[Map[String, Any]] = {
        for (datum <- data) yield datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]].toArray))
    }
}

/**
 * Deserializes a support vector machine model
 */
class SVMDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[SupportVectorMachine](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new SupportVectorMachine
        model.deserialize(filename)
        model
    }
}