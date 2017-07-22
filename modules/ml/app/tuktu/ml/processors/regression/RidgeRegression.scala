package tuktu.ml.processors.regression

import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.ml.models.regression.RidgeRegression

/**
 * Trains a ridge regression classifier
 */
class RidgeRegressionTrainProcessor(resultName: String) extends BaseMLTrainProcessor[RidgeRegression](resultName) {
    var dataField = ""
    var labelField = ""
    var lambda: Double = _
    var trainOnNewData: Boolean = false
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        lambda = (config \ "lambda").as[Double]
        trainOnNewData = (config \ "train_on_new_data").asOpt[Boolean].getOrElse(false)
        
        super.initialize(config)
    }
    
    override def instantiate(data: List[Map[String, Any]]): RidgeRegression =
        new RidgeRegression(lambda)
        
    // Trains the Ridge regression classifier
    override def train(data: List[Map[String, Any]], model: RidgeRegression): RidgeRegression = {
        val records = for (datum <- data) yield {
            // Get the data
            (datum(dataField).asInstanceOf[Seq[Double]].toArray,
                    datum(labelField).asInstanceOf[Double])
        }
        
        // Train the regression model
        val trainData = records.map(_._1).toArray
        val labels = records.map(_._2).toArray
        model.addData(trainData, labels)
        
        // Check if we need to retrain
        if (trainOnNewData)
            model.train

        model
    }
}

/**
 * Applies a Ridge regression model to predict a y-value
 */
class RidgeRegressionApplyProcessor(resultName: String) extends BaseMLApplyProcessor[RidgeRegression](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: RidgeRegression): List[Map[String, Any]] = {
        for (datum <- data) yield {
            val newDatum = datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]]))
            newDatum
        }
    }
}

/**
 * Deserializes a Ridge regression model
 */
class RidgeRegressionDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[RidgeRegression](resultName) {
    var lambda: Double = _
    
    override def initialize(config: JsObject) {
        lambda = (config \ "lambda").as[Double]
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new RidgeRegression(lambda)
        model.deserialize(filename)
        model
    }
}