package tuktu.nlp.processors

import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.nlp.models.TFIDF
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor

/**
 * Adds documents to the word vector of a TF-IDF model
 */
class TFIDFTrainProcessor(resultName: String) extends BaseMLTrainProcessor[TFIDF](resultName) {
    var field: String = _
    
    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        super.initialize(config)
    }
    
    override def instantiate(): TFIDF = new TFIDF()
    
    // Adds a document to the word count vector
    override def train(data: List[Map[String, Any]], model: TFIDF): TFIDF = {
        data.foreach(datum => {
            val value = datum(field)
            // Check field type
            value match {
                case dtm: Seq[String] => model.addDocument(dtm.toList)
                case dtm: Any => model.addDocument(dtm.toString)
            }
        })
        
        model
    }
}

/**
 * Applies TF-IDF computation to a document
 */
class TFIDFApplyProcessor(resultName: String) extends BaseMLApplyProcessor[TFIDF](resultName) {
    var field: String = _
    
    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        super.initialize(config)
    }
    
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: TFIDF): List[Map[String, Any]] = {
        for (datum <- data) yield
            datum + (resultName -> {
                // Check field type
                datum match {
                    case dtm: Seq[String] => model.computeScores(dtm.toList)
                    case dtm: Any => model.computeScores(dtm.toString)
                }
            })
    }
}

/**
 * Deserializes a TF-IDF model
 */
class TFIDFDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[TFIDF](resultName) {
    override def deserializeModel(filename: String) = {
        val model = new TFIDF()
        model.deserialize(filename)
        model
    }
}