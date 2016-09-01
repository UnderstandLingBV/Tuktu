package tuktu.dlib.generators

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._
import tuktu.dlib.utils.europeana

case class LinksPacket( links: Seq[String] )


/**
 * Actor that queries the Europeana API
 */
class EuropeanaActor(parentActor: ActorRef, query: String, maxresult: Option[Int]) extends Actor with ActorLogging 
{
    def receive() = 
    {   
        case ip: InitPacket => 
        {
            // total = 0
            maxresult match{
              case None => self ! LinksPacket( europeana.callEuropeana( query, 1, 0 )(maxresult) )
              case Some(mr) => self ! LinksPacket( europeana.callEuropeana( query, 1, 0 )(maxresult).take(mr) ) 
            }
            
        }
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case lpacket: LinksPacket => 
        {
            // Send back to parent for pushing into channel
            parentActor ! lpacket.links.head

            // Continue with remaining links if any
            lpacket.links.tail.isEmpty match {
              case true => self ! new StopPacket
              case false => self ! LinksPacket( lpacket.links.tail )
            }
        }
    }
}

/**
 * Queries the Europeana API and returns pointers to the resulting records.
 */
class EuropeanaGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    override def _receive = 
    {
        case config: JsValue => 
        {
            // Get the Europeana url to query        
            val query = (config \ "query").as[String]
            val apikey = (config \ "apikey").as[String]
            val maxresult = (config \ "maxresult").asOpt[Int] 
            val url =  query + "&wskey=" + apikey
         
            // Create actor and kickstart
            val europeanaActor = Akka.system.actorOf(Props(classOf[EuropeanaActor], self, url, maxresult))
            europeanaActor ! new InitPacket()
        }
        case link: String => channel.push(DataPacket(List(Map(resultName -> link))))
    }
}

