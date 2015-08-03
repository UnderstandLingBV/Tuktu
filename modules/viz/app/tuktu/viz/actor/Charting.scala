package tuktu.viz.actor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import java.nio.channels.ClosedChannelException
import play.api.libs.iteratee.Enumerator
import akka.actor.ActorRef
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.actor.PoisonPill
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

case class GetEnumerator()
case class EnumeratorReply(
        enumerator: Enumerator[String],
        history: List[String]
)
case class GetChartReply(
        actor: ActorRef,
        historical: Boolean
)
case class SetHistorical(
        historical: Boolean
)

case class HealthCheckRequest()
case class GetChartRequest(
        name: String,
        expiration: Long,
        historical: Boolean,
        overwriteHistorical: Boolean
)
case class DeleteChartRequest(
        name: String
)

/**
 * Charting actor that keeps track of data for potential historical rendering and streaming data
 */
class ChartingActor(name: String, parent: ActorRef, expiration: Long, isHistorical: Boolean) extends Actor with ActorLogging {
    // The broadcasting channel to be used
    val (enumerator, channel) = Concurrent.broadcast[String]
    // Keep track of historical data
    val history = collection.mutable.ListBuffer.empty[String]
    // Keep track of latest sent data packet
    var lastPacket: Long = System.currentTimeMillis
    var historical = isHistorical
    
    def receive = {
        case ge: GetEnumerator => 
            sender ! new EnumeratorReply(enumerator, history.toList)
        case sh: SetHistorical =>
            historical = sh.historical
        case packet: JsObject => {
            lastPacket = System.currentTimeMillis
            try {
                // Add to history, then push
                if (historical)
                    history += packet.toString
                channel.push(packet.toString)
            }
            catch {
                case e: ClosedChannelException => {}
            }
        }
        case hcr: HealthCheckRequest => {
            // Check if our last data was too long ago and we need to clean up
            if (System.currentTimeMillis - lastPacket > expiration)
                parent ! new DeleteChartRequest(name)
        }
        case _ => {}
    }
}

/**
 * Supervisor actor that keeps track of all visualization child actors
 */
class ChartingActorSupervisor() extends Actor with ActorLogging {
    // Map to keep track of running charts, with a reference to the actor and a flag indicating whether
    // or not the chart is historical
    val runningCharts = collection.mutable.Map.empty[String, (ActorRef, Boolean)]
    
    def receive = {
        case cdr: GetChartRequest => {
            // Check if this one already exists or not
            if (runningCharts.contains(cdr.name)) {
                // Set historical, if required
                if (cdr.overwriteHistorical && runningCharts(cdr.name)._2 != cdr.historical)
                    runningCharts(cdr.name)._1 ! new SetHistorical(cdr.historical)
                
                sender ! new GetChartReply(runningCharts(cdr.name)._1, runningCharts(cdr.name)._2)
            }
            else {
                // Create the actor, historical or not
                val chartActor = Akka.system.actorOf(
                        Props(classOf[ChartingActor], cdr.name, self, cdr.expiration, cdr.historical),
                        name = "tuktu.viz.ChartActor." + cdr.name
                )
                
                // Add and send back
                runningCharts += cdr.name -> (chartActor, cdr.historical)
                sender ! new GetChartReply(chartActor, cdr.historical)
            }
        }
        case dcr: DeleteChartRequest => {
            // Delete this chart
            if (runningCharts.contains(dcr.name)) {
                runningCharts(dcr.name)._1 ! PoisonPill
                runningCharts -= dcr.name
            }
        }
        case _ => {}
    }
}