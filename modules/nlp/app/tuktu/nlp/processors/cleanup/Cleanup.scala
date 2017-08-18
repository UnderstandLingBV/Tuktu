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
import tuktu.api.utils
import java.io.InputStream
import play.api.Play
import play.api.Play.current
import java.io.IOException
import com.vdurmont.emoji.EmojiParser
import java.text.Normalizer

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
            case a: Array[String] => clean(a.toSeq, datum)
            case a: Seq[String] => clean(a, datum)
            case a: Any => clean(List(a.toString), datum).head
          }
                    
          datum + (resultName -> result)
        }
    })
    
    def clean(seq: Seq[String], datum: Map[String, Any]): Seq[String]
}

class OddCharacterRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    def flattenToAscii(string: String) = {
        val normalized = Normalizer.normalize(string, Normalizer.Form.NFD)
        normalized.toList.filter {_ <= '\u007F'} mkString("") replaceAll("[^a-zA-Z ]", "")
    }
    
    override def initialize(config: JsObject) {
        super.initialize(config)
    }
    
    def clean(seq: Seq[String], datum: Map[String, Any]) = {
        seq map flattenToAscii
    }
}

/**
 * Removes punctuation from a given sequence of Strings.
 */
class PunctuationRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    // Regex for punctuation
    val punc =  """(\p{P})""".r
 
    def clean(seq: Seq[String], datum: Map[String, Any]) = seq.filterNot(punc.pattern.matcher(_).matches)     
}

/**
 * Removes emojis from a given sequence of Strings.
 */
class EmojiRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    def clean(seq: Seq[String], datum: Map[String, Any]) = seq.map(EmojiParser.removeAllEmojis(_))
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
  
    def clean(seq: Seq[String], datum: Map[String, Any]) = seq.filter(_.length() > n)
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
  
    def clean(seq: Seq[String], datum: Map[String, Any]) = {
      if(uppercase) {
        seq.map(_.toUpperCase) 
      } else {
        seq.map(_.toLowerCase)
      }      
    }
}

/**
 * Stopword remover
 */
class StopwordRemoverProcessor(resultName: String) extends BaseCleaner(resultName) {
    // The language for stopwords
    var lang: String = _

    // Looks for the initial part in a language tag, like nl_NL = nl or nl = nl
    val languageMatcher = """(\p{L}*)(_.*)?""".r
  
    override def initialize(config: JsObject) {
      super.initialize(config)
      // Minimum chars
      lang = (config \ "lang").as[String]
    }
  
    def clean(seq: Seq[String], datum: Map[String, Any]) = {
      // Get the language
      val language = utils.evaluateTuktuString(lang, datum) match {
        case languageMatcher(lang,_) => lang
      }
      
      seq.filterNot( Stopwords.get(language).contains )      
    }    
}

/**
 * Singleton object to read Stopwords only once (for a given language).
 */
object Stopwords {
    // Contains a set of stopwords per language.
    val stopwordsAllLanguages = collection.mutable.Map[String, Set[String]]()
    
    // Get stopwords for a specific language.
    def get(language: String) = {
      stopwordsAllLanguages.getOrElseUpdate(language, readStopWords(language))
    }
    
    // Read stopwords from disk.
    def readStopWords(language: String) = {
      val filePath = "stopwords/"+language
      Play.resourceAsStream(filePath) match {
        case Some(stream) => scala.io.Source.fromInputStream( stream ).getLines.toSet
        case _ => throw new IOException("file not found: " + filePath)
      }
    }
}