package tuktu.ml.processors.decisiontrees

import play.api.libs.json.JsObject
import tuktu.ml.models.decisitiontrees.GradientTreeBoost
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * Trains a decision tree classifier
 */
class GradientTreeBoostTrainProcessor(resultName: String) extends BaseMLTrainProcessor[GradientTreeBoost](resultName) {
    var dataField = ""
    var labelField = ""
    var numTrees: Int = _
    var maxNodes: Int = _
    var shrinkage: Double = _
    var samplingRate: Double =_
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        numTrees = (config \ "num_trees").as[Int]
        maxNodes = (config \ "max_nodes").asOpt[Int].getOrElse(6)
        shrinkage = (config \ "shrinkage").asOpt[Double].getOrElse(0.005)
        samplingRate = (config \ "sampling_rate").asOpt[Double].getOrElse(0.7)
        
        super.initialize(config)
    }
    
    override def instantiate(): GradientTreeBoost =
        new GradientTreeBoost
        
    // Trains the linear regression classifier
    override def train(data: List[Map[String, Any]], model: GradientTreeBoost): GradientTreeBoost = {
        val records = for (datum <- data) yield {
            // Get the data
            (datum(dataField).asInstanceOf[Seq[Double]].toArray,
                    {
                        datum(labelField) match {
                            case a: Double => a.toInt
                            case a: String => a.toInt
                            case a: Int => a
                            case a: Any => a.toString.toInt
                        }
                    })
        }
        
        // Train the regression model
        val trainData = records.map(_._1).toArray
        val labels = records.map(_._2).toArray
        model.train(trainData, labels, numTrees, maxNodes, shrinkage, samplingRate)

        model
    }
}

/**
 * Applies a decision tree model to predict a y-value
 */
class GradientTreeBoostApplyProcessor(resultName: String) extends BaseMLApplyProcessor[GradientTreeBoost](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: GradientTreeBoost): List[Map[String, Any]] = {
        for (datum <- data) yield datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]].toArray))
    }
}

/**
 * Deserializes a decision tree model
 */
class GradientTreeBoostDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[GradientTreeBoost](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new GradientTreeBoost
        model.deserialize(filename)
        model
    }
}