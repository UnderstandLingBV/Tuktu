package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.tartarus.snowball.SnowballStemmer
import org.tartarus.snowball.ext.dutchStemmer
import org.tartarus.snowball.ext.porterStemmer
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Snowball's a piece of text, based on a given language.
 */
class SnowballProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName: String = _
    var language: String = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]        
        language = (config \ "language").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
          
          // Get the language
          val lang = utils.evaluateTuktuString(language, datum)
          
          val stemmer = {
            lang.toLowerCase match {
              case "nl" | "nl_nl" => new dutchStemmer
              case "en" | "en_en" | "en_uk" | _ => new porterStemmer
              //TODO add more languages
            }            
          }

          val result = datum(fieldName) match {
            case a: Array[String] => snowball(a.toSeq, stemmer)
            case a: Seq[String] => snowball(a, stemmer)
            case a: Any => snowball(List(a.toString), stemmer)
          }
                    
          datum + (resultName -> result)
        }
    })
    
    // Convert a sequence into a Snowball representation.
    def snowball (text: Seq[String], stemmer: SnowballStemmer) = {
      text.map{ token =>
            {
              stemmer.setCurrent(token)
              stemmer.stem
              stemmer.getCurrent
            }
          }
    }
    
}