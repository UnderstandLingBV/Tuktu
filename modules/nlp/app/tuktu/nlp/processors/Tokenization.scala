package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

import edu.stanford.nlp.international.arabic.process.ArabicTokenizer

import java.io.StringReader
import tuktu.api.utils

/**
 * Tokenizes a piece of data
 */
class TokenizerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName: String = _
    var asString: Boolean = _
    var language: Option[String] = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
        asString = (config \ "as_string").asOpt[Boolean].getOrElse(false)
        // Language specifity?
        language = (config \ "language").asOpt[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Tokenize
            val fieldValue = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            
            // See if we need to apply a language-specific tokenizer
            val tokens = language match {
                case Some(lang) => utils.evaluateTuktuString(lang, datum) match {
                    case l: String if l == "ar" => tuktu.nlp.models.NLP.tokenize(fieldValue, Some("ar"))
                    case _ => tuktu.nlp.models.NLP.defaultTokenization(fieldValue)
                }
                case None => tuktu.nlp.models.NLP.defaultTokenization(fieldValue)
            }

            // See if we need to concat into a space-separated string
            if (asString)
                datum + (resultName -> tokens.mkString(" "))
            else
                datum + (resultName -> tokens)
        }
    })
}