package tuktu.nlp.processors

import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.nlp.models.Word2Vec
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLApplyProcessor

/**
 * Deserializes a Google Word2Vec Trained Set
 */
class ReadGoogleWord2VecProcessor(resultName: String) extends BaseMLDeserializeProcessor[Word2Vec](resultName) {
    var binary: Boolean = _

    override def initialize(config: JsObject) {
        binary = (config \ "binary").asOpt[Boolean].getOrElse(false)
        super.initialize(config)
    }

    override def deserializeModel(filename: String) = {
        val model = new Word2Vec()
        if (binary)
            model.loadBinaryModel(filename)
        else
            model.loadTextModel(filename)
        model
    }
}

/**
 * Applies Word2Vec computation to a document
 */
class Word2VecNearestWordsProcessor(resultName: String) extends BaseMLApplyProcessor[Word2Vec](resultName) {
    var field: String = _
    var top: Int = _

    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        top = (config \ "top").asOpt[Int].getOrElse(10)
        super.initialize(config)
    }

    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Word2Vec): List[Map[String, Any]] = {
        for (datum <- data) yield {
            datum + (resultName -> {
                // Check field type
                datum(field) match {
                    case dtm: Seq[String] => model.wordsNearest(dtm, top)
                    case dtm: String      => model.wordsNearest(List(dtm), top)
                    case dtm              => model.wordsNearest(List(dtm.toString), top)
                }
            })
        }
    }
}