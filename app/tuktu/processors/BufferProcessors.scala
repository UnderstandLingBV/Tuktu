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

/**
 * This actor is used to buffer stuff in
 */
class BufferActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
    
    var sync: Boolean = false
    var buffer = collection.mutable.ListBuffer[Map[String, Any]]()
    
    def receive() = {
        case s: Boolean => sync = s
        case "release" => {
            // Create datapacket and clear buffer
            val dp = new DataPacket(buffer.toList.asInstanceOf[List[Map[String, Any]]])
            buffer.clear
            
            // Determine if we need to send back the result or not
            sync match { 
                case false => remoteGenerator ! dp
                case true => sender ! Await.result((remoteGenerator ? dp).mapTo[DataPacket], timeout.duration)
            }
            
        }
        case item: Map[String, Any] => buffer += item
    }
}

/**
 * Buffers datapackets until we have a specific amount of them
 */
class SizeBufferProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(1 seconds)
    
    var maxSize = -1
    var curCount = 0
    var sync = false
    
    // Set up the buffering actor
    val bufferActor = Akka.system.actorOf(Props(classOf[BufferActor], genActor))
    
    override def initialize(config: JsValue) = {
        maxSize = (config \ "size").as[Int]
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
        
        // Initialize buffering actor
        bufferActor ! sync
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Iterate over our data and add to the buffer
        data.data.foreach(datum => bufferActor ! datum)
        
        // Increase counter
        curCount += 1
        
        // See if we need to release
        val newData = {
            if (curCount == maxSize) sync match {
                case true => {
                    // Release and obtain result
                    curCount = 0
                    Await.result((bufferActor ? "release").mapTo[DataPacket], timeout.duration)
                }
                case false => {
                    // Send the relase but forget the result
                    curCount = 0
                    bufferActor ! "release"
                    data
                }
            } else data
        }
        
        Future {newData}
    }) compose Enumeratee.onEOF(() => {
        bufferActor ! "release"
        bufferActor ! PoisonPill
    })
}

/**
 * Buffers datapackets for a given amount of time and then releases the buffer for processing
 */
class TimeBufferProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(1 seconds)
    
    var interval = -1
    var cancellable: Cancellable = null
    
    // Set up the buffering actor
    val bufferActor = Akka.system.actorOf(Props(classOf[BufferActor], genActor))
    
    override def initialize(config: JsValue) = {
        interval = (config \ "interval").as[Int]
        
        // Schedule periodic release
        cancellable =
            Akka.system.scheduler.schedule(interval milliseconds,
            interval milliseconds,
            bufferActor,
            "release"
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Iterate over our data and add to the buffer
        data.data.foreach(datum => bufferActor ! datum)
        
        Future {data}
    }) compose Enumeratee.onEOF(() => {
        cancellable.cancel
        bufferActor ! "release"
        bufferActor ! PoisonPill
    })
}