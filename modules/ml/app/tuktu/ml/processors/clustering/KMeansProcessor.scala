package tuktu.ml.processors.clustering

import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.models.clustering.KMeans
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor
import play.api.libs.json.JsObject

/**
 * Trains/clusters data based on K-means
 */
class KMeansTrainProcessor(resultName: String) extends BaseMLTrainProcessor[KMeans](resultName) {
    var dataField: String = _
    var k: Int = _
    var maxIter: Option[Int] = _
    var runs: Option[Int] = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        k = (config \ "k").as[Int]
        maxIter = (config \ "max_iterations").asOpt[Int]
        runs = (config \ "runs").asOpt[Int]
        
        super.initialize(config)
    }
    
    override def instantiate(): KMeans = new KMeans
        
    override def train(data: List[Map[String, Any]], model: KMeans): KMeans = {
        val records = for (datum <- data) yield
            // Get the data
            datum(dataField).asInstanceOf[Seq[Double]].toArray
        
        // Do the clustering
        model.cluster(records.toArray, k, maxIter, runs)

        model
    }
}

/**
 * Applies K-means as a classification model
 */
class KMeansApplyProcessor(resultName: String) extends BaseMLApplyProcessor[KMeans](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: KMeans): List[Map[String, Any]] = {
        for (datum <- data) yield datum + (resultName -> model.predict(datum(dataField).asInstanceOf[Seq[Double]].toArray))
    }
}

/**
 * Deserializes a K-means mdoel
 */
class KMeansDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[KMeans](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new KMeans
        model.deserialize(filename)
        model
    }
}