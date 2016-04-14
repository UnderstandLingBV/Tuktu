package tuktu.ml.processors.decisiontrees

import play.api.libs.json.JsObject
import tuktu.ml.models.decisitiontrees.DecisionTree
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * Trains a decision tree classifier
 */
class DecisionTreeTrainProcessor(resultName: String) extends BaseMLTrainProcessor[DecisionTree](resultName) {
    var dataField = ""
    var labelField = ""
    var maxNodes: Int = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        maxNodes = (config \ "max_nodes").as[Int]
        
        super.initialize(config)
    }
    
    override def instantiate(): DecisionTree =
        new DecisionTree
        
    // Trains the linear regression classifier
    override def train(data: List[Map[String, Any]], model: DecisionTree): DecisionTree = {
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
        model.train(trainData, labels, maxNodes)

        model
    }
}

/**
 * Applies a decision tree model to predict a y-value
 */
class DecisionTreeApplyProcessor(resultName: String) extends BaseMLApplyProcessor[DecisionTree](resultName) {
    var dataField = ""
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        
        super.initialize(config)
    }
    
    // Classify using our logistic regression model
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: DecisionTree): List[Map[String, Any]] = {
        for (datum <- data) yield datum + (resultName -> model.classify(datum(dataField).asInstanceOf[Seq[Double]].toArray))
    }
}

/**
 * Deserializes a decision tree model
 */
class DecisionTreeDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[DecisionTree](resultName) {
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new DecisionTree
        model.deserialize(filename)
        model
    }
}