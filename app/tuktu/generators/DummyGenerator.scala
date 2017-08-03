package tuktu.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.ActorRef
import akka.actor.Cancellable
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.pattern.ask
import tuktu.api._
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicInteger
import play.api.Logger

object DummyHelper {
    def valToType(value: String, outputType: String) = outputType match {
        case "double"   => value.toDouble
        case "int"      => value.toInt
        case "boolean"  => value.toBoolean
        case "jsobject" => Json.parse(value).as[JsObject]
        case "jsarray"  => Json.parse(value).as[JsArray]
        case _          => value
    }
}

/**
 * Just generates dummy strings every tick
 */
class DummyGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var schedule: Cancellable = null
    var maxAmount: Option[Int] = None
    var message: Any = _
    val amountSent = new AtomicInteger(0)

    override def _receive = {
        case config: JsValue =>
            // Get the message to send
            message = DummyHelper.valToType(
                (config \ "message").as[String],
                (config \ "type").asOpt[String].getOrElse("string").toLowerCase)

            // See if we need to stop at some point
            maxAmount = (config \ "max_amount").asOpt[Int]

            // Get the ticking frequency
            val tickInterval = (config \ "interval").as[Int]

            // Determine initial waiting time before sending
            val initialDelay = {
                if ((config \ "send_immediately").asOpt[Boolean].getOrElse(false))
                    Duration.Zero
                else
                    tickInterval milliseconds
            }

            // Set up the scheduler
            schedule = Akka.system.scheduler.schedule(
                initialDelay,
                tickInterval milliseconds,
                self,
                0)

        case sp: StopPacket =>
            if (schedule != null) schedule.cancel
            cleanup

        case 0 =>
            maxAmount match {
                // check if we need to stop
                case Some(amnt) =>
                    if (amountSent.getAndIncrement >= amnt)
                        self ! new StopPacket
                    else
                        channel.push(DataPacket(List(Map(resultName -> message))))

                case None =>
                    channel.push(DataPacket(List(Map(resultName -> message))))
            }
    }
}

/**
 * Generates random numbers
 */
class RandomGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    val r = scala.util.Random
    var maxNum: Int = _
    var schedule: Cancellable = _

    override def _receive = {
        case config: JsValue =>
            // Get the ticking frequency
            val tickInterval = (config \ "interval").as[Int]
            maxNum = (config \ "max").as[Int]

            // Set up the scheduler
            schedule = Akka.system.scheduler.schedule(
                tickInterval milliseconds,
                tickInterval milliseconds,
                self,
                0)

        case sp: StopPacket =>
            if (schedule != null) schedule.cancel
            cleanup

        case 0 =>
            channel.push(DataPacket(List(Map(resultName -> r.nextInt(maxNum)))))
    }
}

/**
 * Generates a list of values
 */
class ListGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def _receive = {
        case config: JsValue =>
            // Get the values
            val vals = (config \ "values").as[List[String]]
            val separate = (config \ "separate").asOpt[Boolean].getOrElse(true)
            val outputType = (config \ "type").asOpt[String].getOrElse("string").toLowerCase

            if (separate) {
                for (value <- vals)
                    channel.push(DataPacket(List(Map(resultName -> DummyHelper.valToType(value, outputType)))))
            } else {
                channel.push(DataPacket(vals.map { v => Map(resultName -> DummyHelper.valToType(v, outputType)) }))
            }
            self ! new StopPacket
    }
}

/**
 * Generates a custom data packet every tick
 */
class CustomPacketGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var schedule: Cancellable = null
    var packet: DataPacket = _
    var maxAmount: Option[Int] = _
    val amountSent = new AtomicInteger(0)

    override def _receive = {
        case config: JsValue =>
            // Get the ticking frequency
            val tickInterval = (config \ "interval").as[Int]
            // Get the packet to send
            val tpkt = (config \ "packet").as[String]
            val js = (config \ "json").asOpt[Boolean].getOrElse(true)
            val jpkt = Json.parse(tpkt).as[List[JsObject]]
            packet = DataPacket(jpkt.map { jobj => if (js) { jobj.as[Map[String, JsValue]] } else { tuktu.api.utils.JsObjectToMap(jobj) } })

            // See if we need to stop at some point
            maxAmount = (config \ "max_amount").asOpt[Int]

            // Determine initial waiting time before sending
            val initialDelay = {
                if ((config \ "send_immediately").asOpt[Boolean].getOrElse(false))
                    Duration.Zero
                else
                    tickInterval milliseconds
            }

            // Set up the scheduler
            schedule = Akka.system.scheduler.schedule(
                initialDelay,
                tickInterval milliseconds,
                self,
                0)

        case sp: StopPacket =>
            if (schedule != null) schedule.cancel
            cleanup

        case 0 =>
            maxAmount match {
                // check if we need to stop
                case Some(amnt) =>
                    if (amountSent.getAndIncrement >= amnt)
                        self ! new StopPacket
                    else
                        channel.push(packet)

                case None =>
                    channel.push(packet)
            }
    }
}