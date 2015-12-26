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

case class OAISetsPacket( sets: Seq[Node] )

class ListSetsActor(parentActor: ActorRef, verb: String) extends Actor with ActorLogging 
{
    
    def receive() = 
    {   
        case ip: InitPacket => self ! OAIRequestPacket( verb )
        
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
            val sets = (rpacket.response \ "ListSets" \ "set" ).toSeq
            // Send back to parent for pushing into channel
            parentActor ! OAISetsPacket( sets )
            // check for resumptionToken
            val rToken = (rpacket.response \ "ListSets" \ "resumptionToken" ).headOption
            rToken match
            {
              case Some( resumptionToken ) => self ! OAIRequestPacket( verb + "&resumptionToken=" + resumptionToken.text ) // keep harvesting
              case None => self ! new StopPacket // harvesting completed
            }
             
        }
    }

}

/**
 * Retrieves the set structure of a repository.
 */
class ListSetsGenerator ( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    var toj: Boolean = _
    var flatten: Boolean = _
    override def receive() = 
    {
        case config: JsValue => 
        {
          // Get the ListRecords parameters        
          val target = (config \ "target").as[String]
          toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
          flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
         
          val verb = target + "?verb=ListSets" 
          
          val harvester = Akka.system.actorOf(Props(classOf[ListSetsActor], self, verb))
          harvester ! new InitPacket()
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
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
        case sets: OAISetsPacket =>{
          val s = (sets.sets map { set => set.toString.trim })
          for (set <- s; if (!set.isEmpty))
          {
            toj match{
              case false => channel.push( new DataPacket( List( Map( resultName -> set ) ) ) )
              case true => {
                val jo = oaipmh.xml2jsObject( set )
                flatten match{
                  case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jo ) ) ) )
                  case false => channel.push( new DataPacket( List( Map( resultName -> jo ) ) ) )
                }
              }
            }
          }
        }
        
        case x => Logger.error("OAI-PMH ListSets generator got unexpected packet " + x + "\r\n")
    }
}