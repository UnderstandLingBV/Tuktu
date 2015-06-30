package tuktu.ml.processors.regression

import play.api.libs.json.JsObject
import tuktu.ml.models.regression.LinearRegression
import tuktu.ml.models.regression.LogisticRegression
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * Trains a logistic regression classifier
 */
class LinearRegressionTrainProcessor(resultName: String) extends BaseMLTrainProcessor[LinearRegression](resultName) {
    var dataField = ""
    var labelField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        
        super.initialize(config)
    }
    
    override def instantiate(): LinearRegression =
        new LinearRegression
        
    // Trains the linear regression classifier
    override def train(data: List[Map[String, Any]], model: LinearRegression): LinearRegression = {
        val records = for (datum <- data) yield {
            // Get the data
            (datum(dataField).asInstanceOf[Seq[Double]].toArray,
                    datum(labelField).asInstanceOf[Double])
        }
        
        // Train the regression model
        val trainData = records.map(_._1).toArray
        val labels = records.map(_._2).toArray
        model.addData(trainData, labels)

        model
    }
}

/**
 * Applies a linear regression model to predict a y-value
 */
class LinearRegressionApplyProcessor(resultName: String) extends BaseMLApplyProcessor[LinearRegression](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: LinearRegression): List[Map[String, Any]] = {
        for (datum <- data) yield
            datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]]))
    }
}

/**
 * Deserializes a linear regression model
 */
class LinearRegressionDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[LinearRegression](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new LinearRegression
        model.deserialize(filename)
        model
    }
}