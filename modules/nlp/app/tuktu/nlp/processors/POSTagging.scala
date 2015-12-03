package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import nl.et4it.POSWrapper
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.Logger
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Performs POS-tagging
 */
class POSTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var taggers = scala.collection.mutable.Map[String, POSWrapper]()

    var lang = ""
    var tokens = ""

    override def initialize(config: JsObject) {
        lang = (config \ "language").as[String]
        tokens = (config \ "tokens").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket((for (datum <- data.data) yield {
            // Get the language
            val language = utils.evaluateTuktuString(lang, datum)
            // Get the tokens
            val tkns = datum(tokens).asInstanceOf[Array[String]]

            // See if the tagger is already loaded
            /*if (!taggers.contains(language)) {
                val tagger = new POSWrapper(language)
                taggers += language -> tagger
            }
            
            // Tag it
            val posTags = taggers(language).tag(tkns)
            * */
            // TODO: Stupid OpenNLP is not thread-safe, fix this later
            try {
                val tagger = new POSWrapper(language)
                val posTags = tagger.tag(tkns)

                datum + (resultName -> posTags)
            } catch {
                case e: Throwable => {
                    Logger.warn("Error trying to POSTag tokens in language " + language + ", are you sure you support this language? Datum will be filtered out, rest should work normally.")
                    Map.empty[String, Any]
                }
            }
        }).filter(!_.isEmpty))
    }) compose Enumeratee.filter((data: DataPacket) => !data.data.isEmpty)
}