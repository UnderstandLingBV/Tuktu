package tuktu.nlp

import scala.collection.JavaConversions.mapAsScalaMap
import scala.concurrent.ExecutionContext.Implicits.global
import nl.et4it._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.concurrent.Future
import play.api.libs.json.JsNull

/**
 * Tokenizes a piece of data
 */
class TokenizerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName = ""
    var asString = false
    
    override def initialize(config: JsValue) = {
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
    
    override def initialize(config: JsValue) = {
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
    
    var langSpec: JsValue = JsNull
    var lang: Option[String] = None
    var tokens = ""
    
    override def initialize(config: JsValue) = {
        langSpec = (config \ "language").as[JsValue]
        lang = (langSpec \ "field").asOpt[String]
        tokens = (config \ "tokens").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {new DataPacket(for (datum <- data.data) yield {
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
            val tkns = datum(tokens).asInstanceOf[Array[String]]
            
            // See if the tagger is already loaded
            if (!taggers.contains(language)) {
                val tagger = new POSWrapper(language)
                taggers += language -> tagger
            }
            // Tag it
            val posTags = taggers(language).tag(tkns)
        
            datum + (resultName -> posTags)
        })}
    })
}