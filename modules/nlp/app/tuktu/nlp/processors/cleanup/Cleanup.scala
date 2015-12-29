package tuktu.nlp.processors.cleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import org.tartarus.snowball.ext.dutchStemmer
import org.tartarus.snowball.ext.porterStemmer
import org.tartarus.snowball.SnowballStemmer

/**
 * Removes punctuation from a given sequence of Strings.
 */
class PunctuationRemoverProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName: String = _
    
    val punc =  """(\p{P})""".r
    
    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]       
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
          
          val result = datum(fieldName) match {
            case a: Array[String] => clean(a.toSeq)
            case a: Seq[String] => clean(a)
            case a: Any => clean(List(a.toString))
          }
                    
          datum + (resultName -> result)
        }
    })

    def clean(seq: Seq[String]) = {
      for (
        token <- seq if (!punc.pattern.matcher(token).matches)
      ) yield token
    }
}