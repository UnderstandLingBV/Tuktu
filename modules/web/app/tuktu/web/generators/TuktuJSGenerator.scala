package tuktu.web.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import tuktu.api._

/**
 * Gets a webpage's content based on REST request
 */
class TuktuJSGenerator(
        referer: String,
        resultName: String,
        processors: List[Enumeratee[DataPacket, DataPacket]],
        senderActor: Option[ActorRef]
) extends TuktuBaseJSGenerator(referer, resultName, processors, senderActor) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Channeling
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    val idString = java.util.UUID.randomUUID.toString
    
    // Every processor but the first gets treated as asynchronous
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> (processor compose utils.logEnumeratee(idString)) &>> sinkIteratee)
        
    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(sActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((d: DataPacket) => {
            val sourceActor = {
                senderActor match {
                    case Some(a) => a
                    case None => sActor
                }
            }
            
            sourceActor ! d
            
            d
        })
        
        def runProcessor() = {
            Enumerator(dp) |>> (processors.head compose sendBackEnum compose utils.logEnumeratee(idString)) &>> sinkIteratee
        }
    }

    def receive() = {
        case ip: InitPacket => {
            // Add ourselves to the cache
            Cache.getOrElse[collection.mutable.Map[String, ActorRef]]("web.hostmap")(collection.mutable.Map.empty) += (referer -> self)
        }
        case config: JsValue => {}
        case sp: StopPacket => {
            // Remove ourselves from the cache
            Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap")
                .getOrElse(collection.mutable.Map[String, ActorRef]()) -= referer
            println("Removing from generator: " + referer)
            
            // Send message to the monitor actor
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "done"
            )
            
            val enum: Enumerator[DataPacket] = Enumerator.enumInput(Input.EOF)
            enum |>> (processors.head compose utils.logEnumeratee(idString)) &>> sinkIteratee

            channel.eofAndEnd           
            self ! PoisonPill
        }
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, dp)
            p.runProcessor()
        }
    }
}