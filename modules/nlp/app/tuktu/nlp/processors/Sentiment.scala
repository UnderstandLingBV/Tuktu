package tuktu.nlp.processors

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import nl.et4it.RBEMEmotion
import nl.et4it.RBEMPolarity
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Performs polarity detection
 */
class RBEMPolarityProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Keep track of our models
    var models = scala.collection.mutable.Map[String, RBEMPolarity]()

    var lang = ""
    var tokens = ""
    var tags = ""

    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
        tags = (config \ "pos").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens from data
            val tkns = datum(tokens) match {
                case t: String => t.split(" ")
                case t: Array[String] => t
                case t: Any => t.toString.split(" ")
            }
            // We need POS-tags before we can do anything, must be given in a field
            val posTags = datum(tags) match {
                case t: String => t.split(" ")
                case t: Array[String] => t
                case t: Any => t.toString.split(" ")
            }

            // See if the model for this language is already loaded
            if (!models.contains(language)) {
                val rbemPol = new RBEMPolarity()
                rbemPol.loadModel(language)
                models += language -> rbemPol
            }

            // Apply polarity detection
            val polarity = models(language).classify(tkns, posTags)
            val pol = {
                if (polarity.getRight > 0) 1
                else if (polarity.getRight < 0) -1
                else 0
            }

            // Add the actual score
            datum + (resultName -> polarity.getRight)
        }
    })
}

/**
 * Performs emotion detection
 */
class RBEMEmotionProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Keep track of our models
    var models = scala.collection.mutable.Map[String, RBEMEmotion]()

    var lang = ""
    var tokens = ""
    var tags = ""

    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
        tags = (config \ "pos").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens from data
            val tkns = datum(tokens) match {
                case t: String => t.split(" ")
                case t: Array[String] => t
                case t: Any => t.toString.split(" ")
            }
            // We need POS-tags before we can do anything, must be given in a field
            val posTags = datum(tags) match {
                case t: String => t.split(" ")
                case t: Array[String] => t
                case t: Any => t.toString.split(" ")
            }

            // See if the model for this language is already loaded
            if (!models.contains(language)) {
                val rbemEmo = new RBEMEmotion()
                rbemEmo.loadModel(language)
                models += language -> rbemEmo
            }

            // Apply emotion detection, normalize
            val emotions = models(language).classify(tkns, posTags, true).asScala.toMap

            // Add the actual score
            datum + (resultName -> emotions)
        }
    })
}