package tuktu.dlib.processors.vocabularies

import akka.pattern.ask
import akka.util.Timeout

import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject

import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.actors._

/**
 * Looks up a vocabulary term in the in-memory vocabulary bank.
 */
class VocabularyLookupProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var lang: Option[String] = _
    var sourceTerm: String = _
    var vocabularyName: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the vocabulary, term, and language to look up
        lang = (config \ "language").asOpt[String]
        sourceTerm = (config \ "term").as[String]
        vocabularyName = (config \ "name").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
      val lfutures = data.data.map( datum =>
      {
        val vocabName = evaluateTuktuString(vocabularyName, datum)
        val source = evaluateTuktuString(sourceTerm, datum)
        val fut = lang match{
          case None => Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ? new TermRequest(vocabName, source)          
          case Some(lng) => {
            val language = evaluateTuktuString(lng, datum)
            Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ? new LangTermRequest(vocabName, source, language)
          }
        }
        fut.map {
          case term: TermResult => term.term match{
            case None => datum
            case Some(t) => datum + ( resultName -> t)
          }
          case lterm: LangTermResult => lterm.langTerm match{
            case None => datum
            case Some(t) => datum + ( resultName -> t)
          }
        }
      })
      Future.sequence( lfutures ).map( x => new DataPacket(x) )
    })  
}