package tuktu.dlib.generators

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._

case class RecordPacket( record: JsObject )

/**
 * Harvests records from a Learning Registry node.
 */
class LearningRegistryGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    override def _receive = 
    {
        case config: JsValue => 
        {
            // Get the Node address and harvesting parameters        
            val node = (config \ "node").as[String]
            val from = (config \ "from").asOpt[String]
            val until = (config \ "until").asOpt[String]
            val param = "/harvest/listrecords" + ((from,until) match{
              case (None,None) => ""
              case (None, Some(u)) => "?until=" + u
              case (Some(f), None) => "?from=" + f
              case (Some(f), Some(u)) => "?from=" + f + "&until=" + u
            })
            
            // Create actor and kickstart
            val lrActor = Akka.system.actorOf(Props(classOf[LearningRegistryActor], self, node, param))
            lrActor ! new InitPacket()
        }
        case record: RecordPacket => channel.push(new DataPacket(List(Map(resultName -> record.record))))
    }
}

/**
 * Actor that harvest the Learning Registry harvest API
 */
class LearningRegistryActor(parentActor: ActorRef, node: String, param: String) extends Actor with ActorLogging 
{
    def receive() = 
    {   
        case ip: InitPacket => 
        {
            harvest( node, param )
        }
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
    }
    
    def harvest(node: String, param: String): Future[Unit] = 
    {
        val future = WS.url(node + param).get
        future.map(response => {
            val res = response.json
            val records = (res \\ "record").map { value => value.as[JsObject] } 
            for( record <- records)
            {
              parentActor ! new RecordPacket( record )
            }
            val token = ( res \ "resumption_token" ).asOpt[String] 
            token match
            {
                case None => self ! new StopPacket
                case Some(rtoken) => harvest( node, "/harvest/listrecords?resumption_token=" + rtoken )
            }
        })
    }
    
}