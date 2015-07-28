package tuktu.ml.processors.timeseries

import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLApplyProcessor
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.ml.models.timeseries.ARIMAModel
import tuktu.ml.models.timeseries.ARIMA
import tuktu.ml.models.timeseries.Autoregression
import breeze.linalg.DenseVector

/**
 * Trains an ARIMA model
 */
class ARIMATrainProcessor(resultName: String) extends BaseMLTrainProcessor[ARIMAModel](resultName) {
    // Variables for p, d, q
    var p: Int = _
    var d: Int = _
    var q: Int = _
    
    // Field with observations
    var dataField: String = _
    
    // Intercept?
    var includeIntercept: Boolean = _
    
    override def initialize(config: JsObject) {
        p = (config \ "p").as[Int]
        d = (config \ "d").as[Int]
        q = (config \ "q").as[Int]
        
        dataField = (config \ "data_field").as[String]
        
        includeIntercept = (config \ "include_intercept").asOpt[Boolean].getOrElse(true)
        
        super.initialize(config)
    }
    
    override def instantiate(): ARIMAModel = ARIMA.fitModel((p, d, q), breeze.linalg.Vector(), includeIntercept)
        
    // Trains the ARIMA model
    override def train(data: List[Map[String, Any]], model: ARIMAModel): ARIMAModel = {
        var newModel = model
        
        // Go over the data
        for (datum <- data) {
            // Get train data
            val trainData = breeze.linalg.Vector(datum(dataField).asInstanceOf[Seq[Double]]: _*)
            
            if (model == null)
                // Initialize model
                newModel = ARIMA.fitModel((p, d, q), trainData, includeIntercept)
            else {
                // Run using pre-computed coefficients
                val diffedTs = ARIMA.differences(trainData, d).toArray.drop(d)
        
                if (p > 0 && q == 0) {
                    val arModel = Autoregression.fitModel(new DenseVector(diffedTs), p)
                    newModel = new ARIMAModel((p, d, q), Array(arModel.c) ++ arModel.coefficients, includeIntercept)
                }
                else
                    new ARIMAModel((p, d, q), newModel.coefficients, includeIntercept)
            }
        }
        
        newModel
    }
}

/**
 * Applies an ARIMA model
 */
class ARIMAApplyProcessor(resultName: String) extends BaseMLApplyProcessor[ARIMAModel](resultName) {
    var dataField: String = _
    var nFuture: Int = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        nFuture = (config \ "n_future").as[Int]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: ARIMAModel): List[Map[String, Any]] = {
        for (datum <- data) yield
            datum + (resultName -> model.forecast(breeze.linalg.Vector(datum(dataField).asInstanceOf[Seq[Double]]: _*), nFuture))
    }
}

class ARIMADeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[ARIMAModel](resultName) {
    // Variables for p, d, q
    var p: Int = _
    var d: Int = _
    var q: Int = _
    
    // Field with observations
    var dataField: String = _
    
    // Intercept?
    var includeIntercept: Boolean = _
    
    override def initialize(config: JsObject) {
        p = (config \ "p").as[Int]
        d = (config \ "d").as[Int]
        q = (config \ "q").as[Int]
        
        dataField = (config \ "data_field").as[String]
        
        includeIntercept = (config \ "include_intercept").asOpt[Boolean].getOrElse(true)
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = ARIMA.fitModel((p, d, q), breeze.linalg.Vector(), includeIntercept)
        model.deserialize(filename)
        model
    }
}