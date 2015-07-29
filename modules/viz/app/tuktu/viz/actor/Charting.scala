package tuktu.viz.actor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsObject
import play.api.libs.json.Json

case class GetEnumerator()

class ChartingActor() extends Actor with ActorLogging {
    // The broadcasting channel to be used
    val (enumerator, channel) = Concurrent.broadcast[String]
    
    def receive = {
        case ge: GetEnumerator => 
            sender ! enumerator
        case packet: JsObject =>
            channel.push(packet.toString)
        case _ => {}
    }
}