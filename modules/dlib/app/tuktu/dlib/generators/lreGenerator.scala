package tuktu.dlib.generators

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.cache.Cache
import play.api.Logger
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.dlib.utils.lre

case class LREIdsPacket( ids: Seq[String] )
case class LREResultPacket( result: JsValue )

/**
 * Actor that queries the LRE API
 */
class LREActor(parentActor: ActorRef, query: String, resultOnly: Boolean) extends Actor with ActorLogging 
{
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def receive() = 
    {   
        case ip: InitPacket => 
        {
            val fresult = lre.callLRE( query, resultOnly )
            fresult map{ result => result match{
                    case value: JsValue => self ? LREResultPacket( value )
                    case identifiers: Seq[String] => self ? LREIdsPacket( identifiers )
                }
            }            
        }
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case lipacket: LREIdsPacket => 
        {
            // Send back to parent for pushing into channel
            parentActor ! lipacket.ids.head

            // Continue with remaining links if any
            lipacket.ids.tail.isEmpty match {
              case true => self ! new StopPacket
              case false => self ! LREIdsPacket( lipacket.ids.tail )
            }
        }
        case lrpacket: LREResultPacket =>
        {
            parentActor ! lrpacket
            self ! StopPacket
        }
    }
}

/**
 * Queries the Learning Resource Exchange (LRE) API and returns the corresponding list of LRE resource identifiers.
 */
class LREGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    override def _receive = 
    {
        case config: JsValue => 
        {
            // Get the LRE query parameters
            val service = (config \ "service").asOpt[String].getOrElse( "http://lresearch.eun.org/" )
            val cnf = (config \ "query").as[String]
            val limit = (config \ "limit").asOpt[Int]
            val query = service + "?cnf=" + cnf + (limit match {
                case Some(lmt) => "&limit=" + lmt
                case None => ""
            })
            val resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(true)
         
            // Create actor and kickstart
            val lreActor = Akka.system.actorOf(Props(classOf[LREActor], self, query, resultOnly))
            lreActor ! new InitPacket()
        }
        case id: String => channel.push(new DataPacket(List(Map(resultName -> id))))
        case lrpacket: LREResultPacket => channel.push(new DataPacket(List(Map(resultName -> lrpacket.result))))
    }
}