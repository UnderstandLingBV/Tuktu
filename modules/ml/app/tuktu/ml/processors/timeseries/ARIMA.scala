package tuktu.ml.processors.timeseries

import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLApplyProcessor
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.ml.models.timeseries.ARIMAModel
import tuktu.ml.models.timeseries.ARIMA

class ARIMATrainProcessor(resultName: String) extends BaseMLTrainProcessor[ARIMAModel](resultName) {
    // Variables for p, d, q
    var p: Int = _
    var d: Int = _
    var q: Int = _
    
    // Field with observations
    var dataField: String = _
    
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def instantiate(): ARIMAModel = null
        
    // Trains the ARIMA model
    override def train(data: List[Map[String, Any]], model: ARIMAModel): ARIMAModel = {
        // Get the train data
        val trainData = for (datum <- data) yield
            datum(dataField).asInstanceOf[Seq[Double]]
        
        //ARIMA.fitModel((p, d, q), ts, includeIntercept, method)
        
        model
    }
}

class ARIMAApplyProcessor(resultName: String) extends BaseMLApplyProcessor[ARIMAModel](resultName) {
    
}

class ARIMADeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[ARIMAModel](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        null
    }
}