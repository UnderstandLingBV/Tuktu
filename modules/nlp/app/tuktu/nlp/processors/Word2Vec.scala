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

class Word2VecSimpleClassifierProcessor(resultName: String) extends BaseMLApplyProcessor[Word2Vec](resultName) {
    var field: String = _
    var candidates: List[List[String]] = _
    var top: Int = _
    var flatten: Boolean = _

    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        candidates = (config \ "candidates").as[List[List[String]]]
        top = (config \ "top").asOpt[Int].getOrElse(1)
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(true)
        
        super.initialize(config)
    }

    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Word2Vec): List[Map[String, Any]] = {
        for (datum <- data) yield {
            datum + (resultName -> {
                // Check field type
                val scores = datum(field) match {
                    case dtm: Seq[String] => model.simpleNearestWordsClassifier(dtm.toList, candidates)
                    case dtm: String      => model.simpleNearestWordsClassifier(dtm.split(" ").toList, candidates)
                    case dtm              => model.simpleNearestWordsClassifier(dtm.toString.split(" ").toList, candidates)
                }
                
                // Flatten if we have to
                if (flatten) scores.head._1 else scores
            })
        }
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

/**
 * Computes a document vector by taking the average of the word vectors - this is a heuristic
 */
class Word2VecAverageWordsVector(resultName: String) extends BaseMLApplyProcessor[Word2Vec](resultName) {
    var field: String = _
    var tfIdfField: Option[String] = _

    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        tfIdfField = (config \ "tfidf_field").asOpt[String]
        super.initialize(config)
    }

    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Word2Vec): List[Map[String, Any]] = {
        for (datum <- data) yield {
            datum + (resultName -> {
                // Get TF-IDF if required
                val tfidf = tfIdfField match {
                    case Some(t) => Some(datum(t).asInstanceOf[Map[String, Double]])
                    case None => None
                }
                // Check field type
                val docVector = datum(field) match {
                    case dtm: Seq[String] => model.getAverageDocVector(dtm, tfidf)
                    case dtm: String      => model.getAverageDocVector(dtm.split(" "), tfidf)
                    case dtm              => model.getAverageDocVector(dtm.toString.split(" "), tfidf)
                }
                
                // Convert to something usable
                (0 to docVector.columns - 1).map(ind => docVector.getDouble(0, ind))
            })
        }
    }
}