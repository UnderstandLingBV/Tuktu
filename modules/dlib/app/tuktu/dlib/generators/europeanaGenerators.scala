package tuktu.dlib.generators

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
import scala.io.Source
import scala.io.Codec

case class LinksPacket( links: Seq[String] )


/**
 * Actor that queries the Europeana API
 */
class EuropeanaActor(parentActor: ActorRef, query: String, apikey: String, maxresult: Option[Int]) extends Actor with ActorLogging 
{
    var total: Int = _
    
    def receive() = 
    {   
        case ip: InitPacket => 
        {
            total = 0
            self ! LinksPacket( callEuropeana( query, 0 ) )
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
    
    /**
     * Utility method to recursively call the Europeana API until either all the results or the requested number of results is reached
     * @param query: a Europeana query without paging (&start) nor apikey (&wskey) parameters
     * @param start: the first result to collect
     * @return A stream of URLs pointing to the resulting Europeana metadata records 
     */
    def callEuropeana( query: String, start: Int ): Stream[String] =
    {
        val encoding: String = "UTF8"
        val src: Source = Source.fromURL( query + "&start=" + (start + 1) + "&wskey=" + apikey )( Codec.apply( encoding ) )
        val json = Json.parse( src.mkString )
        src.close()
        val itemsCount: Int = (json \ "itemsCount").as[Int]
        val tr: Int = (json \ "totalResults").as[Int]
        val totalResults: Int = maxresult match{
          case None => tr
          case Some(max) => if ((max < tr) && (max > 0)) max else tr
        } 
        val results: Stream[String] = (json \ "items").as[Stream[JsObject]].map{ x => (x \ "link").as[String] }
        total = total + itemsCount
        if (totalResults > total)
        {
            return results ++ callEuropeana( query, total )
        }
        else
        {
            return results
        }       
    }
    
}


/**
 * Queries the Europeana API and returns pointers to the resulting records.
 */
class EuropeanaGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    override def receive() = 
    {
        case config: JsValue => 
        {
            // Get the Europeana url to query        
            val query = (config \ "query").as[String]
            val apikey = (config \ "apikey").as[String]
            val maxresult = (config \ "maxresult").asOpt[Int]       
         
            // Create actor and kickstart
            val europeanaActor = Akka.system.actorOf(Props(classOf[EuropeanaActor], self, query, apikey, maxresult))
            europeanaActor ! new InitPacket()
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case link: String => channel.push(new DataPacket(List(Map(resultName -> link))))
        case x => Logger.error("Time generator got unexpected packet " + x + "\r\n")
    }
}

