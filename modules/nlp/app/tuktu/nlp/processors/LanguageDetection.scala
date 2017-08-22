package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.optimaize.langdetect.profiles.LanguageProfileReader

import nl.et4it.LIGA
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.text.TextObjectFactory
import com.optimaize.langdetect.text.CommonTextObjectFactories

/**
 * Performs language detection
 */
class LIGAProcessor(resultName: String) extends BaseProcessor(resultName) {
    // LIGA has only one model
    val liga = new LIGA()
    liga.loadModel()

    var fieldName: String = _

    override def initialize(config: JsObject) {
        fieldName = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the field on which we should perform the language detection
            val text = {
                if (datum(fieldName).isInstanceOf[JsString]) datum(fieldName).asInstanceOf[JsString].value
                else datum(fieldName).asInstanceOf[String]
            }
            // Get language
            val language = liga.classify(text)

            datum + (resultName -> language)
        }
    })
}

/**
 * Runs a simpler, yet more elaborate language detection algorithm based on n-grams only
 * See https://github.com/optimaize/language-detector/blob/master/README.md
 */
class LangDetProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Load all language profiles
    val languageProfiles = new LanguageProfileReader().readAllBuiltIn
    val languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard)
        .withProfiles(languageProfiles)
        .minimalConfidence(0.85d)
        .build
    var textObjectFactory: TextObjectFactory = _
    
    var fieldName: String = _
    
    override def initialize(config: JsObject) {
        fieldName = (config \ "field").as[String]
        (config \ "short_texts").asOpt[Boolean] match {
            case None => textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText
            case Some(s) if s => textObjectFactory = CommonTextObjectFactories.forDetectingShortCleanText
            case Some(s) if !s => textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.map(datum => {
            val textObject = datum(fieldName) match {
                case s: String => s
                case a: Any => a.toString
            }
            datum + (resultName -> {
                val lang = languageDetector.detect(textObject)
                if (!lang.isPresent) "UNKNOWN" else lang.get.getLanguage
            })
        }))
    })
}