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
 * Generic Base class for the cleaner classes.
 */
abstract class BaseCleaner(resultName: String) extends BaseProcessor(resultName) {
    // field containing the sequence of tokens.
    var fieldName: String = _
  
    override def initialize(config: JsObject) {
        // Get field
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
    
    def clean(seq: Seq[String]): Seq[String]
}

/**
 * Removes punctuation from a given sequence of Strings.
 */
class PunctuationRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    // Regex for punctuation
    val punc =  """(\p{P})""".r
 
    def clean(seq: Seq[String]) = seq.filterNot(punc.pattern.matcher(_).matches)     
}

/**
 * Removes punctuation from a given sequence of Strings.
 */
class NCharsRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    // N chars to remove
    var n: Int = _
  
    override def initialize(config: JsObject) {
      super.initialize(config)
      // Minimum chars
      n = (config \ "n").as[Int]
    }
  
    def clean(seq: Seq[String]) = seq.filter(_.length() > n)
}

/**
 * Changes the case of a sequence of Strings to all upper or lowercase.
 */
class CaseConverterProcessor(resultName: String) extends BaseCleaner(resultName) {
    // N chars to remove
    var uppercase: Boolean = _
  
    override def initialize(config: JsObject) {
      super.initialize(config)
      // Minimum chars
      uppercase = (config \ "uppercase").asOpt[Boolean].getOrElse(false)
    }
  
    def clean(seq: Seq[String]) = {
      if(uppercase) {
        seq.map(_.toUpperCase) 
      } else {
        seq.map(_.toLowerCase)
      }      
    }
}