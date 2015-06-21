package tuktu.nlp.processors

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import nl.et4it.LIGA
import nl.et4it.POSWrapper
import nl.et4it.RBEMPolarity
import nl.et4it.RBEMEmotion
import nl.et4it.Tokenizer
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Tokenizes a piece of data
 */
class TokenizerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName = ""
    var asString = false
    
    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
        asString = (config \ "as_string").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {new DataPacket(for (datum <- data.data) yield {
            // Tokenize
            val fieldValue = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            val tokens = Tokenizer.tokenize(fieldValue)
            
            // See if we need to concat into a space-separated string
            if (asString)
                datum + (resultName -> tokens.mkString(" "))
            else
                datum + (resultName -> tokens)
        })}
    })
}

/**
 * Performs language detection
 */
class LIGAProcessor(resultName: String) extends BaseProcessor(resultName) {
    // LIGA has only one model
    var liga = new LIGA()
    liga.loadModel()
    
    var fieldName = ""
    
    override def initialize(config: JsObject) {
        fieldName = (config \ "field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {new DataPacket(for (datum <- data.data) yield {
            // Get the field on which we should perform the language detection
            val text = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            // Get language
            val language = liga.classify(text)
        
            datum + (resultName -> language)
        })}
    })
}

/**
 * Performs POS-tagging
 */
class POSTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var taggers = scala.collection.mutable.Map[String, POSWrapper]()
    
    var lang = ""
    var tokens = ""
    
    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {new DataPacket((for (datum <- data.data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens
            val tkns = datum(tokens).asInstanceOf[Array[String]]
            
            // See if the tagger is already loaded
            /*if (!taggers.contains(language)) {
                val tagger = new POSWrapper(language)
                taggers += language -> tagger
            }
            
            // Tag it
            val posTags = taggers(language).tag(tkns)
            * */
            // TODO: Stupid OpenNLP is not thread-safe, fix this later
            try {
                val tagger = new POSWrapper(language)
                val posTags = tagger.tag(tkns)
                
                datum + (resultName -> posTags)
            }
            catch {
                case _: Throwable => Map[String, Any]()
            }
        }).filter(!_.isEmpty))}
    }) compose Enumeratee.filter((data: DataPacket) => !data.data.isEmpty)
}

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
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {new DataPacket(for (datum <- data.data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens from data
            val tkns = datum(tokens).asInstanceOf[Array[String]]
            // We need POS-tags before we can do anything, must be given in a field
            val posTags = datum(tags).asInstanceOf[Array[String]]
            
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
        })}
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
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {new DataPacket(for (datum <- data.data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens from data
            val tkns = datum(tokens).asInstanceOf[Array[String]]
            // We need POS-tags before we can do anything, must be given in a field
            val posTags = datum(tags).asInstanceOf[Array[String]]
            
            // See if the model for this language is already loaded
            if (!models.contains(language)) {
                val rbemEmo = new RBEMEmotion()
                rbemEmo.loadModel(language)
                models += language -> rbemEmo
            }
            
            // Apply emotion detection, normalize
            val emotions = models(language).classify(tkns, posTags, true).asScala
            
            // Add the actual score
            datum + (resultName -> emotions)
        })}
    })
}