package tuktu.ml.processors.clustering

import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.models.clustering.KMeans
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor
import play.api.libs.json.JsObject

class KMeansProcessor(resultName: String) extends BaseMLTrainProcessor[KMeans](resultName) {
    override def initialize(config: JsObject) {
        
    }
    
    override def instantiate(): KMeans =
        new KMeans
        
    override def train(data: List[Map[String, Any]], model: KMeans): KMeans = {
        model
    }
}

class KMeansApplyProcessor(resultName: String) extends BaseMLApplyProcessor[KMeans](resultName) {
    override def initialize(config: JsObject) {
        
    }
}

class KMeansDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[KMeans](resultName) {
    override def initialize(config: JsObject) {
        
    }
}