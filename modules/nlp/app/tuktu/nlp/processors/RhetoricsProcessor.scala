package tuktu.nlp.processors

import tuktu.api.BaseProcessor
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import com.understandling.Rhetorics
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils

/**
 * Gets the rhetorical figures present in a sentence
 */
class RhetoricsProcessor(resultName: String) extends BaseProcessor(resultName) {
    var tokens: String =_
    var tags: String = _
    var lang: String = _
    
    override def initialize(config: JsObject) {
        tokens = (config \ "tokens").as[String]
        tags = (config \ "pos").as[String]
        lang = (config \ "language").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
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
            
            // Run the persuasion algorithm
            datum + (resultName -> Rhetorics.find(tkns, posTags, language))
        })
    })
}