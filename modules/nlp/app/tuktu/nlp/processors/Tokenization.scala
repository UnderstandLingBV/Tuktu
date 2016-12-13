package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import nl.et4it.Tokenizer
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Tokenizes a piece of data
 */
class TokenizerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName: String = _
    var asString: Boolean = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
        asString = (config \ "as_string").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Tokenize
            val fieldValue = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            val tokens = {
                // Remove links and mentions, add stuff around sentence closures
                val clean = fieldValue.replaceAll("(http:|ftp:|https:|www.)[^ ]+[ |\r|\n|\t]", " ").replaceAll("(http:|ftp:|https:|www.).*", "")
                    .replaceAll("#[0-9a-zA-z]+", " ").replaceAll("@[0-9a-zA-z]+", " ").replaceAll("\"", " \" ")
                    .replaceAll("\\.", " . ").replaceAll("!", " ! ").replaceAll("\\?", " ? ").replaceAll(",", " , ")
                    .replaceAll(" +", " ")
                // Now split on space
                clean.split(" ").map(_.trim).filter(!_.isEmpty)
            }

            // See if we need to concat into a space-separated string
            if (asString)
                datum + (resultName -> tokens.mkString(" "))
            else
                datum + (resultName -> tokens)
        }
    })
}