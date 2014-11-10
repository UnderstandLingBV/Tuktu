package tuktu.nlp

import scala.collection.JavaConversions.mapAsScalaMap
import scala.concurrent.ExecutionContext.Implicits.global

import nl.et4it._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Tokenizes a piece of data
 */
class TokenizerProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get fields
        val fieldName = (config \ "field").as[String]
        val asString = (config \ "as_string").asOpt[Boolean].getOrElse(false)
        
        new DataPacket(for (datum <- data.data) yield {
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
        })
    })
}

/**
 * Performs language detection
 */
class LIGAProcessor(resultName: String) extends BaseProcessor(resultName) {
    var liga = new LIGA()
    liga.loadModel()
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        val fieldName = (config \ "field").as[String]
        new DataPacket(for (datum <- data.data) yield {
            // Get the field on which we should perform the language detection
            val text = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            // Get language
            val language = liga.classify(text)
        
            datum + (resultName -> language)
        })
    })
}

/**
 * Performs POS-tagging
 */
class POSTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var taggers = scala.collection.mutable.Map[String, POSWrapper]()
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        val langSpec = (config \ "language").as[JsValue]
        val lang = (langSpec \ "field").asOpt[String]
        
        new DataPacket(for (datum <- data.data) yield {
            // Get the language, either fixed or from a data field
            val language = {
                lang match {
                    case Some(l) => {
                        // Need to get language from a data-field
                        datum(l).asInstanceOf[String]
                    }
                    case None => {
                        // Language is hard-coded
                        langSpec.as[String]
                    } 
                }
            }
            // Get the tokens
            val tokens = datum((config \ "tokens").as[String]).asInstanceOf[Array[String]]
            
            // See if the tagger is already loaded
            if (!taggers.contains(language)) {
                val tagger = new POSWrapper(language)
                taggers += language -> tagger
            }
            // Tag it
            val posTags = taggers(language).tag(tokens)
        
            datum + (resultName -> posTags)
        })
    })
}

/**
 * Performs polarity detection
 */
class RBEMPolarityProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Keep track of our models
    var models = scala.collection.mutable.Map[String, RBEMPolarity]()
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        val langSpec = (config \ "language").as[JsValue]
        val lang = (langSpec \ "field").asOpt[String]
        
        new DataPacket(for (datum <- data.data) yield {
            // Get the language, either fixed or from a data field
            val language = {
                lang match {
                    case Some(l) => {
                        // Need to get language from a data-field
                        datum(l).asInstanceOf[String]
                    }
                    case None => {
                        // Language is hard-coded
                        langSpec.as[String]
                    } 
                }
            }
            // Get the tokens from data
            val tokens = datum((config \ "tokens").as[String]).asInstanceOf[Array[String]]
            // We need POS-tags before we can do anything, must be given in a field
            val posTags = datum((config \ "pos").as[String]).asInstanceOf[Array[String]]
            
            // See if the model for this language is already loaded
            if (!models.contains(language)) {
                val rbemPol = new RBEMPolarity()
                rbemPol.loadModel(language)
                models += language -> rbemPol
            }
            
            // Apply polarity detection
            val polarity = models(language).classify(tokens, posTags)
            val pol = {
                if (polarity.getRight > 0) 1
                else if (polarity.getRight < 0) -1
                else 0
            }
            
            // Add the actual score
            datum + (resultName -> polarity.getRight)
        })
    })
}