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

case class OAIFormatsPacket( formats: Seq[Node] )

/**
 * Actor that obtains the metadata formats available from a repository
 */
class ListFormatsActor(parentActor: ActorRef, verb: String) extends Actor with ActorLogging 
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
            val formats = (rpacket.response \ "ListMetadataFormats" \ "metadataFormat" ).toSeq
            // Send back to parent for pushing into channel
            parentActor ! OAIFormatsPacket( formats )
            self ! new StopPacket // harvesting completed
        }
    }
}

/**
 * Retrieves the metadata formats available from a repository. An optional argument restricts the request to the formats available for a specific item.
 */
class ListMetadataFormatsGenerator ( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
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
          val identifier =  (config \ "identifier").asOpt[String]
          toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
          flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
         
          val verb = target + "?verb=ListMetadataFormats" + (identifier match{
            case None => ""
            case Some( id ) => "&identifier=" + id
          })
          
          val harvester = Akka.system.actorOf(Props(classOf[ListFormatsActor], self, verb))
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
        case formats: OAIFormatsPacket =>{
          val f = (formats.formats map { frmt => frmt.toString.trim })
          for (format <- f; if (!format.isEmpty))
          {
            toj match{
              case false => channel.push( new DataPacket( List( Map( resultName -> format ) ) ) )
              case true => {
                val jo = oaipmh.xml2jsObject( format )
                flatten match{
                  case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jo ) ) ) )
                  case false => channel.push( new DataPacket( List( Map( resultName -> jo ) ) ) )
                }
              }
            }
          }
        }
        
        case x => Logger.error("OAI-PMH ListMetadataFormats generator got unexpected packet " + x + "\r\n")
    }
}