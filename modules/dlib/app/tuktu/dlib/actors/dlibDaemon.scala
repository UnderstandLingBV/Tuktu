package tuktu.dlib.actors


import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.Identify
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsObject
import tuktu.api.ClusterNode
import tuktu.api.DataPacket
import tuktu.api.DeleteRequest
import tuktu.api.InitPacket
import tuktu.api.PersistRequest
import tuktu.api.ReadRequest
import tuktu.api.ReadResponse
import tuktu.api.ReplicateRequest
import tuktu.api.utils
import scala.util.Random
import tuktu.api.DeleteActionRequest


case class JVocabulary(vocabName:String, vocabulary: JsObject)
case class MVocabulary(vocabName:String, vocabulary: Map[String,Any])
case class Term(vocabName:String, source: String, target: Any)
case class LangTerm( vocabName: String, source: String, language: String, target: Any)
case class VocabularyRequest(vocabName: String)
case class TermRequest(vocabName: String, source: String)
case class LangTermRequest(vocabName: String, source: String, language: String)
case class VocabularyResult(voc: Option[Map[String,Any]])
case class TermResult(term: Option[Any])
case class LangTermResult(langTerm: Option[String])
case class VocabRemovalRequest(name: String)


/**
 * Daemon for DLIB Vocabulary Bank
 */
class DLIBDaemon() extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // vocabulary bank
    private var vocabularies = new scala.collection.mutable.HashMap[String,Map[String,Any]]()
    
    def receive() = {
        case ip: InitPacket => {}
        case jvoc: JVocabulary => vocabularies.put( jvoc.vocabName, utils.JsObjectToMap( jvoc.vocabulary ) )
        case mvoc: MVocabulary => vocabularies.put( mvoc.vocabName, mvoc.vocabulary )
        case term: Term => {
          vocabularies.get( term.vocabName ) match{
            case None => vocabularies.put( term.vocabName, Map(term.source -> term.target) )
            case Some( vocabulary ) => vocabularies.put( term.vocabName, (vocabulary + (term.source -> term.target)) )
          }
        }
        case lterm: LangTerm => {
          vocabularies.get( lterm.vocabName ) match{
            case None => vocabularies.put( lterm.vocabName, Map(lterm.source -> Map(lterm.language -> lterm.target) ) )
            case Some( vocabulary ) => {
              val langTerm = vocabulary.get(lterm.source)
              langTerm match {
                case None => vocabularies.put( lterm.vocabName, vocabulary + (lterm.source -> Map(lterm.language -> lterm.target) ) )
                case Some(lt) => vocabularies.put( lterm.vocabName, vocabulary + (lterm.source -> ((lt.asInstanceOf[Map[String,Any]]) + (lterm.language -> lterm.target) ) ) )
              } 
            }
          }
        }
        case vreq: VocabularyRequest => sender ! new VocabularyResult( vocabularies.get( vreq.vocabName ) )
        case treq: TermRequest => sender ! new TermResult(vocabularies.get( treq.vocabName ) match
        {
          case None => None
          case Some(vocabulary) => vocabulary.get(treq.source)
        })
        case ltreq: LangTermRequest => sender ! new LangTermResult(vocabularies.get( ltreq.vocabName ) match
        {
          case None => None
          case Some(vocabulary) => vocabulary.get(ltreq.source) match
          {
            case None => None
            case Some(langString) => langString.asInstanceOf[Map[String,String]].get(ltreq.language)
          }
        })
        case vrr: VocabRemovalRequest => vocabularies.remove(vrr.name)
    }
}