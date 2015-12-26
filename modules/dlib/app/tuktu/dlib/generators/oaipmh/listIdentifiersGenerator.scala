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
 * Actor that harvests an OAI-PMH target
 */
class ListIdentifiersActor(parentActor: ActorRef, verb: String, params: String) extends Actor with ActorLogging 
{
    
    def receive() = 
    {   
        case ip: InitPacket => self ! OAIRequestPacket( verb + params )
        
        case request: OAIRequestPacket => {
          // println( "requested URL: " + request.request )
          val response = oaipmh.harvest( request.request )
          // println( "response received: " + response.toString )
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
            // println( records.toString )
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
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case error: OAIErrorPacket => {
          toj match{
            case false => channel.push( new DataPacket( List( Map( resultName -> error.error ) ) ) )
            case true => {
              val xmlText = error.error.toString
              val jsonText = org.json.XML.toJSONObject( xmlText ).toString( 4 )
              val jsobj: JsObject = Json.parse( jsonText ).as[JsObject]
              flatten match{
                case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jsobj ) ) ) )
                case false => channel.push( new DataPacket( List( Map( resultName -> jsobj ) ) ) )
              }
            }
          }
        } 
        case ids: OAIIdentifiersPacket =>{
          val recs = (ids.ids map { id => id.toString.trim })
          for (record <- recs; if (!record.isEmpty))
          {
            toj match{
              case false => channel.push( new DataPacket( List( Map( resultName -> record ) ) ) )
              case true => {
                val jsonText = org.json.XML.toJSONObject( record ).toString( 4 )
                val jsobj: JsObject = Json.parse( jsonText ).as[JsObject]
                val jo = (jsobj \ jsobj.keys.head).as[JsObject]
                flatten match{
                  case true => channel.push( new DataPacket( List( tuktu.api.utils.JsObjectToMap( jo ) ) ) )
                  case false => channel.push( new DataPacket( List( Map( resultName -> jo ) ) ) )
                }
              }
            }
          }
        }
        
        case x => Logger.error("OAI-PMH ListIdentifiers generator got unexpected packet " + x + "\r\n")
    }

}
