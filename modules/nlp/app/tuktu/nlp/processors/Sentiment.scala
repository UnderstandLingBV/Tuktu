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
import tuktu.nlp.models.URBEM
import com.github.jfasttext.JFastText

/**
 * Performs polarity detection
 */
class RBEMPolarityProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Keep track of our models
    var models = scala.collection.mutable.Map[String, RBEMPolarity]()

    var lang = ""
    var tokens = ""
    var tags = ""
    var discretize: Boolean = _

    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
        tags = (config \ "pos").as[String]
        discretize = (config \ "discretize").asOpt[Boolean].getOrElse(false)
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
            datum + (resultName -> {
                if (discretize) pol else polarity.getRight
            })
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
    var discretize: Boolean = _

    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
        tags = (config \ "pos").as[String]
        discretize = (config \ "discretize").asOpt[Boolean].getOrElse(false)
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
            val emotions = {
                val emos = models(language).classify(tkns, posTags, true).asScala.toMap
                if (discretize) emos.map(e => e._1 -> {
                    if (e._2 > 0.0) 1.0 else if (e._2 < 0.0) -1.0 else 0.0
                }) else emos.map(e => e._1 -> e._2.toDouble)
            }

            // Add the actual score
            datum + (resultName -> emotions)
        }
    })
}

class URBEMProcessor(resultName: String) extends BaseProcessor(resultName) {
    val instances = collection.mutable.Map.empty[String, URBEM]
    var language: String = _
    var tokens: String = _
    var vectorModel: String = _
    var seedWords: Map[String, List[List[String]]] = _
    var leftFlips: List[List[String]] = _
    var rightFlips: List[List[String]] = _
    var seedCutoff: String = _
    var negationCutoff: String = _
    
    override def initialize(config: JsObject) {
        language = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
        vectorModel = (config \ "vector_file").as[String]
        seedWords = (config \ "seed_words").as[Map[String, List[String]]].map {sw =>
            sw._1 -> sw._2.map(_.split(" ").toList)
        }
        leftFlips = (config \ "left_flips").as[List[String]].map(_.split(" ").toList)
        rightFlips = (config \ "right_flips").as[List[String]].map(_.split(" ").toList)
        seedCutoff = (config \ "seed_cutoff").as[String]
        negationCutoff = (config \ "negation_cutoff").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.map {datum =>
            // Get URBEM model
            val lang = utils.evaluateTuktuString(language, datum)
            val urbem = if (instances.contains(lang)) instances(lang) else {
                val jft = new JFastText
                jft.loadModel(utils.evaluateTuktuString(vectorModel, datum))
                val u = new URBEM(lang, jft)
                instances += lang -> u
                u
            }
            
            // Set seed words
            urbem.setSeedWords(seedWords, rightFlips, leftFlips)
            
            // Classify
            datum + (resultName -> {
                val scores = urbem.predict(datum(tokens) match {
                    case t: String => t
                    case t: Array[String] => t.mkString(" ")
                    case t: Any => t.toString
                }, utils.evaluateTuktuString(seedCutoff, datum).toDouble, utils.evaluateTuktuString(negationCutoff, datum).toDouble)
                    .toList.sortBy(_._2)(Ordering[Double].reverse) // Sort highest score first
                
                // Get the highest scoring class, if any
                if (scores.head == 0.0 || (scores.size > 1 && scores(0) == scores(1))) "neutral"
                else scores.head._1
            })
        })
    })
}