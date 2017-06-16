package tuktu.nlp.processors

import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.nlp.models.Word2Vec
import play.api.libs.json.JsObject
import tuktu.ml.processors.BaseMLApplyProcessor
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j

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
 * Classifies a document by taking it's averaged word vectors and matching it against averaged word vectors for sets of candidate words 
 */
class Word2VecSimpleClassifierProcessor(resultName: String) extends BaseMLApplyProcessor[Word2Vec](resultName) {
    var field: String = _
    var candidates: List[List[String]] = _
    var top: Int = _
    var flatten: Boolean = _
    var cutoff: Option[Double] = _

    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        candidates = (config \ "candidates").as[List[List[String]]]
        top = (config \ "top").asOpt[Int].getOrElse(1)
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(true)
        cutoff = (config \ "cutoff").asOpt[Double]
        
        super.initialize(config)
    }

    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Word2Vec): List[Map[String, Any]] = {
        for (datum <- data) yield {
            datum + (resultName -> {
                // Check field type
                val scores = {
                    val s = datum(field) match {
                        case dtm: Seq[String] => model.simpleNearestWordsClassifier(dtm.toList, candidates)
                        case dtm: String      => model.simpleNearestWordsClassifier(dtm.split(" ").toList, candidates)
                        case dtm              => model.simpleNearestWordsClassifier(dtm.toString.split(" ").toList, candidates)
                    }
                
                    // Cutoff
                    cutoff match {
                        case Some(c) => {
                            // Get only those labels that have a score higher or equal to the cutoff
                            val f = s.filter(_._2 >= c)
                            if (f.isEmpty) List((-1, 0.0)) else f
                        }
                        case None => s
                    }
                }
                
                // Flatten if we have to
                if (flatten) scores.head._1 else scores.take(top)
            })
        }
    }
}

/**
 * This classifier is similar to the one above but instead of looking at averaged word vectors, it looks at vectors word-by-word
 * and sees if there is a close-enough overlap between one or more candidate set words and the sentence's words.
 */
class Word2VecWordBasedClassifierProcessor(resultName: String) extends BaseMLApplyProcessor[Word2Vec](resultName) {
    var field: String = _
    val candidateVectors = collection.mutable.ListBuffer.empty[List[INDArray]]
    var model: Word2Vec = null
    var top: Int = _
    var flatten: Boolean = _
    var cutoff: Double = _
    var candidates: List[List[String]] = _

    override def initialize(config: JsObject) {
        field = (config \ "data_field").as[String]
        candidates = (config \ "candidates").as[List[List[String]]]
        top = (config \ "top").asOpt[Int].getOrElse(1)
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(true)
        cutoff = (config \ "cutoff").asOpt[Double].getOrElse(0.7)
        
        super.initialize(config)
    }

    override def applyModel(resultName: String, data: List[Map[String, Any]], model: Word2Vec): List[Map[String, Any]] = {
        // Initialize model and candidate vectors
        if (this.model == null) {
            this.model = model
            candidates.foreach {candidateSet =>
                // Compute the vectors for each candidate
                candidateVectors += candidateSet.filter(model.wordMap.contains(_)).map(model.wordMap(_))
            }
        }
        
        for (datum <- data) yield {
            datum + (resultName -> {
                // Check field type
                val scores = (datum(field) match {
                    case dtm: Seq[String] => model.simpleWordOverlapClassifier(dtm.toList, candidateVectors.toList, cutoff)
                    case dtm: String      => model.simpleWordOverlapClassifier(dtm.split(" ").toList, candidateVectors.toList, cutoff)
                    case dtm              => model.simpleWordOverlapClassifier(dtm.toString.split(" ").toList, candidateVectors.toList, cutoff)
                }) map {score =>
                    if (score._2 < cutoff) (-1, 0.0) else score
                }
                
                // Flatten if we have to
                if (flatten) scores.head._1 else scores.take(top)
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