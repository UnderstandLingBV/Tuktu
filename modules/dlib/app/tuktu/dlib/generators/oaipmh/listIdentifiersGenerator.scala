package tuktu.dlib.generators.oaipmh

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._
import scala.xml._
import tuktu.dlib.utils.oaipmh

case class OAIIdentifiersPacket( ids: Seq[Node] )

/**
 * Actor that harvests the record identifiers from an OAI-PMH target
 */
class ListIdentifiersActor(parentActor: ActorRef, verb: String, params: String) extends Actor with ActorLogging 
{
    
    def receive() = 
    {   
        case ip: InitPacket => self ! OAIRequestPacket( verb + params )
        
        case request: OAIRequestPacket => {
          val response = oaipmh.harvest( request.request )
          (response \\ "error").headOption match{
              case None => self ! OAIResponsePacket( response )
              case Some( err ) => {
                // Send back to parent for pushing into channel
                parentActor ! OAIErrorPacket( response )
                self ! new StopPacket
              }
          }
        }
        
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        
        case rpacket: OAIResponsePacket => 
        {
            // send records to parent
            val headers = (rpacket.response \ "ListIdentifiers" \ "header" ).toSeq
            // Send back to parent for pushing into channel
            parentActor ! OAIIdentifiersPacket( headers )
            // check for resumptionToken
            val rToken = (rpacket.response \ "ListIdentifiers" \ "resumptionToken" ).headOption
            rToken match
            {
              case Some( resumptionToken ) => self ! OAIRequestPacket( verb + "&resumptionToken=" + resumptionToken.text ) // keep harvesting
              case None => self ! new StopPacket // harvesting completed
            }
             
        }
    }

}

/**
 * Harvests metadata record identifiers from an OAI-PMH target repository.
 */
class ListIdentifiersGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    var toj: Boolean = _
    var flatten: Boolean = _
    
    override def _receive = 
    {
        case config: JsValue => 
        {
          // Get the ListRecords parameters        
          val target = (config \ "target").as[String]
          val metadataPrefix = (config \ "metadataPrefix").as[String]
          val from = (config \ "from").asOpt[String]
          val until = (config \ "until").asOpt[String]
          val sets = (config \ "sets").asOpt[List[String]]
          toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
          flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
         
          val verb = target + "?verb=ListIdentifiers" 
          val params = "&metadataPrefix=" + metadataPrefix + (from match{
            case None => ""
            case Some(f) => "&from=" + f
          }) + (until match{
            case None => ""
            case Some(u) => "&until=" + u
          })
            
          sets match{
            case None => val harvester = Akka.system.actorOf(Props(classOf[ListIdentifiersActor], self, verb, params)); harvester ! new InitPacket()
            case Some( s ) => for ( set <- s ) { val harvester = Akka.system.actorOf(Props(classOf[ListIdentifiersActor], self, verb, params + "&set=" + set)); harvester ! new InitPacket() } 
          }  
        }
        case error: OAIErrorPacket => {
          toj match{
            case false => channel.push( new DataPacket( List( Map( resultName -> error.error ) ) ) )
            case true => {
              val jsobj: JsObject = oaipmh.xml2jsObject( error.error.toString )
              flatten match{
                case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jsobj ) ) ) )
                case false => channel.push( new DataPacket( List( Map( resultName -> jsobj ) ) ) )
              }
            }
          }
        } 
        case ids: OAIIdentifiersPacket =>{
          val identifiers = (ids.ids map { id => id.toString.trim })
          for (identifier <- identifiers; if (!identifier.isEmpty))
          {
            toj match{
              case false => channel.push( new DataPacket( List( Map( resultName -> identifier ) ) ) )
              case true => {
                val jo = oaipmh.xml2jsObject( identifier )
                flatten match{
                  case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jo ) ) ) )
                  case false => channel.push( new DataPacket( List( Map( resultName -> jo ) ) ) )
                }
              }
            }
          }
        }
    }

}
