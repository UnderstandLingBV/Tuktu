package tuktu.processors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api._
import play.api.libs.json._
import play.api.cache.Cache
import scala.collection.mutable.ListBuffer

/**
 * Unwind the Seq field of a single data packet into separate data packets (one per element in the Seq to unwind)
 */
class FieldUnwindProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Set up the splitting actor
    val unwindActor = Akka.system.actorOf(Props(classOf[UnwinderActor], genActor))
    
    
    var field: String = _ // the field to unwind
    
    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }
    
    
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val futures = Future.sequence( 
            for (datum <- unwind( data.data ) ) yield unwindActor ? datum
        )
        
        futures.map {
            case _ => data
        }
    }) compose Enumeratee.onEOF(() => unwindActor ! new StopPacket)
    
    def unwind( list: List[Map[String,Any]] ): List[Map[String,Any]] =
    {
        val buf = scala.collection.mutable.ListBuffer.empty[Map[String,Any]]
      
        for (map <- list)
        {
          if (map.contains( field ))
          {
            map( field ) match{
              case seq: Seq[Any] => for ( elem <- seq ) { buf += map + ( field -> elem ) }
              case jarray: JsArray => for ( jvalue <- jarray.as[List[JsValue]] ) { buf += map + ( field -> jvalue ) }
              case elem: Any => buf += map + ( field -> elem )
            }
          }
        }
        return buf.toList
    }
    
}

/**
 * Actor for forwarding the split data packets
 */
class UnwinderActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    remoteGenerator ! new InitPacket
    
    def receive() = {
        case sp: StopPacket => {
            remoteGenerator ! sp
            self ! PoisonPill
        }
        case item: Map[String, Any] => {
            // Directly forward
            remoteGenerator ! new DataPacket(List(item))
            sender ! "ok"
        }
    }
}