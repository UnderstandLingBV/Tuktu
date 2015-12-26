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

case class OAIRequestPacket( request: String )
case class OAIResponsePacket( response: Elem )
case class OAIErrorPacket( error: Elem )
case class OAIRecordsPacket( records: Seq[Node] )

/**
 * Actor that harvests an OAI-PMH target
 */
class HarvesterActor(parentActor: ActorRef, verb: String, params: String) extends Actor with ActorLogging 
{
    
    def receive() = 
    {   
        case ip: InitPacket => self ! OAIRequestPacket( verb + params )
        
        case request: OAIRequestPacket => {
          val response = oaipmh.harvest( request.request )
          (response \\ "error").headOption match{
              case None => self ! OAIResponsePacket( response )
              case Some( err ) => {
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
            val records = (rpacket.response \ "ListRecords" \ "record" \ "metadata" ).flatMap( _.child ).toSeq
            // println( records.toString )
            // Send back to parent for pushing into channel
            parentActor ! OAIRecordsPacket( records )
            // check for resumptionToken
            val rToken = (rpacket.response \ "ListRecords" \ "resumptionToken" ).headOption
            rToken match
            {
              case Some( resumptionToken ) => self ! OAIRequestPacket( verb + "&resumptionToken=" + resumptionToken.text ) // keep harvesting
              case None => self ! new StopPacket // harvesting completed
            }             
        }
    }
}

/**
 * Harvests metadata records from an OAI-PMH target repository.
 */
class ListRecordsGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
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
          val metadataPrefix = (config \ "metadataPrefix").as[String]
          val from = (config \ "from").asOpt[String]
          val until = (config \ "until").asOpt[String]
          val sets = (config \ "sets").asOpt[List[String]]
          toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
          flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
         
          val verb = target + "?verb=ListRecords" 
          val params = "&metadataPrefix=" + metadataPrefix + (from match{
            case None => ""
            case Some(f) => "&from=" + f
          }) + (until match{
            case None => ""
            case Some(u) => "&until=" + u
          })
            
          sets match{
            case None => val harvester = Akka.system.actorOf(Props(classOf[HarvesterActor], self, verb, params)); harvester ! new InitPacket()
            case Some( s ) => for ( set <- s ) { val harvester = Akka.system.actorOf(Props(classOf[HarvesterActor], self, verb, params + "&set=" + set)); harvester ! new InitPacket() } 
          }  
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
        case records: OAIRecordsPacket =>{
          val recs = (records.records map { rec => rec.toString.trim })
          for (record <- recs; if (!record.isEmpty))
          {
            toj match{
              case false => channel.push( new DataPacket( List( Map( resultName -> record ) ) ) )
              case true => {
                val jo = oaipmh.xml2jsObject( record )
                flatten match{
                  case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jo ) ) ) )
                  case false => channel.push( new DataPacket( List( Map( resultName -> jo ) ) ) )
                }
              }
            }
          }
        }
        
        case x => Logger.error("OAI-PMH ListRecords generator got unexpected packet " + x + "\r\n")
    }

}

