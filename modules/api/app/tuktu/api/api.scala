package tuktu.api

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.GenTraversableOnce
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.util.Timeout
import play.api.Application
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Cont
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumeratee.CheckDone
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.mvc.AnyContent
import play.api.mvc.Request
import scala.concurrent.ExecutionContext
import java.util.concurrent.atomic.AtomicInteger

case class DataPacket(
        data: List[Map[String, Any]]) extends java.io.Serializable {
    def isEmpty: Boolean = data.isEmpty
    def nonEmpty: Boolean = data.nonEmpty
    def filter(f: Map[String, Any] => Boolean): DataPacket = DataPacket(data.filter(f))
    def filterNot(f: Map[String, Any] => Boolean): DataPacket = DataPacket(data.filterNot(f))
    def map(f: Map[String, Any] => Map[String, Any]): DataPacket = DataPacket(data.map(f))
    def flatMap(f: Map[String, Any] => GenTraversableOnce[Map[String, Any]]): DataPacket = DataPacket(data.flatMap(f))
    def foreach(f: Map[String, Any] => Unit): Unit = data.foreach(f)
    def size: Int = data.size
}

case class DispatchRequest(
    configName: String,
    config: Option[JsValue],
    isRemote: Boolean,
    returnRef: Boolean,
    sync: Boolean,
    sourceActor: Option[ActorRef])

case class InitPacket()
case class StopPacket()
case class ErrorPacket()

case class BackPressurePacket()
case class DecreasePressurePacket()
case class BackPressureNotificationPacket(
    idString: String)

case class ResponsePacket(
    json: JsValue)

case class RequestPacket(
    request: Request[AnyContent],
    isInitial: Boolean)

case class HealthCheck()
case class HealthReply()

case class ClusterNode(
    host: String,
    akkaPort: Int,
    UIPort: Int)

/**
 * Monitor stuff
 */

sealed abstract class MPType
case object BeginType extends MPType
case object EndType extends MPType
case object CompleteType extends MPType

case class MonitorPacket(
    typeOf: MPType,
    uuid: String,
    branch: String,
    amount: Integer,
    timestamp: Long = System.currentTimeMillis)

case class MonitorOverviewRequest()
case class MonitorOverviewResult(
    runningJobs: Map[String, AppMonitorObject],
    finishedJobs: Map[String, AppMonitorObject],
    subflows: Map[String, String])

case class MonitorLastDataPacketRequest(
    flow_name: String,
    processor_id: String)

case class ProcessorMonitorPacket(
    typeOf: MPType,
    uuid: String,
    processor_id: String,
    data: DataPacket,
    timestamp: Long = System.currentTimeMillis)

case class AppMonitorObject(
        uuid: String,
        configName: String,
        instances: Int,
        startTime: Long,
        stopOnError: Boolean,
        var finished_instances: Int = 0,
        var endTime: Option[Long] = None,
        expirationTime: Long = Cache.getAs[Long]("mon.finish_expiration").getOrElse(play.api.Play.current.configuration.getLong("tuktu.monitor.finish_expiration").getOrElse(30L)) * 60 * 1000,
        errorExpirationTime: Long = Cache.getAs[Long]("mon.error_expiration").getOrElse(play.api.Play.current.configuration.getLong("tuktu.monitor.error_expiration").getOrElse(40320L)) * 60 * 1000,
        errors: collection.mutable.Map[String, String] = collection.mutable.Map.empty,
        actors: collection.mutable.Set[ActorRef] = collection.mutable.Set.empty,
        flowDataPacketCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        flowDatumCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorDataPackets: collection.mutable.Map[String, collection.mutable.Map[MPType, DataPacket]] = collection.mutable.Map.empty,
        processorDataPacketCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorDatumCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorBeginTimes: collection.mutable.Map[String, collection.mutable.Queue[Long]] = collection.mutable.Map.empty,
        processorDurations: collection.mutable.Map[String, collection.mutable.ListBuffer[Long]] = collection.mutable.Map.empty) {
    def expire(current: Long = System.currentTimeMillis, force: Boolean = true) {
        if (force || endTime != None)
            endTime = Some(current)
    }
    def is_expired(current: Long = System.currentTimeMillis): Boolean = endTime match {
        case None      => false
        case Some(end) => end + { if (errors.isEmpty) expirationTime else errorExpirationTime } <= current
    }
    def is_expired: Boolean = is_expired()
}

case class AppMonitorPacket(
        val actor: ActorRef,
        val status: String,
        val timestamp: Long = System.currentTimeMillis) {
    def getName = actor.path.toStringWithoutAddress
    def getParentName = actor.path.parent.toStringWithoutAddress
}
case class AppMonitorUUIDPacket(
    uuid: String,
    status: String,
    timestamp: Long = System.currentTimeMillis)

case class AddMonitorEventListener()
case class RemoveMonitorEventListener()

case class AppInitPacket(
    uuid: String,
    configName: String,
    instanceCount: Int,
    stopOnError: Boolean,
    mailbox: Option[ActorRef] = None,
    timestamp: Long = System.currentTimeMillis)
case class SubflowMapPacket(
    mailbox: ActorRef,
    subflows: List[ActorRef])
case class ErrorNotificationPacket(
    uuid: String,
    configName: String,
    processorName: String,
    input: String,
    error: Throwable)
case class ClearFlowPacket(
    uuid: String)
/**
 * End monitoring stuff
 */

abstract class BaseProcessor(resultName: String) {
    def initialize(config: JsObject): Unit = {}
    def processor(): Enumeratee[DataPacket, DataPacket] = ???
}

/**
 * When merging branches, multiple EOFs are received by the processor where the merge is happening
 * This processor is prepended by the Dispatcher to processors where merging happens to handle these EOFs by ignoring all but the last one
 */
object BranchMergeProcessor {
    // Keep track of how many EOFs were seen by each processor defined by their UUID
    val map = collection.mutable.Map[String, AtomicInteger]()

    // Creates a new EOF ignoring Enumeratee
    def ignoreEOFs[M](EOFCount: Int): Enumeratee[M, M] = {
        // Generate unique UUID and add to the map
        var uuid = java.util.UUID.randomUUID.toString
        while (map.contains(uuid))
            uuid = java.util.UUID.randomUUID.toString
        map += uuid -> new AtomicInteger(0)

        /**
         * An enumeratee that passes all input through until EOFCount EOFs are reached, redeeming the final iteratee with EOF as the left over input. 
         * Based on: https://github.com/playframework/playframework/blob/2.3.x/framework/src/iteratees/src/main/scala/play/api/libs/iteratee/Enumeratee.scala#L672-L681
         */
        new Enumeratee.CheckDone[M, M] {

            def step[A](k: Input[M] => Iteratee[M, A]): Input[M] => Iteratee[M, Iteratee[M, A]] = {
                return {
                    case in @ (Input.El(_) | Input.Empty) => new Enumeratee.CheckDone[M, M] { def continue[A](k: Input[M] => Iteratee[M, A]) = Cont(step(k)) } &> k(in)
                    case Input.EOF => {
                        // We have reached an EOF, increment and check if we have reached the designated count
                        if (map(uuid).incrementAndGet == EOFCount) {
                            // We are done, sent out EOF for real
                            map -= uuid
                            Done(Cont(k), Input.EOF)
                        } else {
                            // We are not done yet, send Empty
                            new Enumeratee.CheckDone[M, M] { def continue[A](k: Input[M] => Iteratee[M, A]) = Cont(step(k)) } &> k(Input.Empty)
                        }
                    }
                }
            }

            def continue[A](k: Input[M] => Iteratee[M, A]) = Cont(step(k))
        }
    }
}

abstract class BaseJsProcessor(resultName: String) extends BaseProcessor(resultName) {
    val jsField = Cache.getAs[String]("web.jsname").getOrElse(Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field"))

    def addJsElement[A <: BaseJsObject](datum: Map[String, Any], element: A): Map[String, Any] = {
        // Get the JS elements
        val newDatum = if (!datum.contains(jsField)) datum + (jsField -> new WebJsOrderedObject(List())) else {
            datum(jsField) match {
                case a: WebJsOrderedObject => datum
                case a: Any                => datum + (jsField -> new WebJsOrderedObject(List()))
            }
        }

        val jsElements = newDatum(jsField).asInstanceOf[WebJsOrderedObject]

        // Add this element to the ordered web js object
        newDatum + (jsField -> new WebJsOrderedObject(
            jsElements.items ++ List(Map(
                resultName -> element))))
    }

    def addJsElements[A <: BaseJsObject](datum: Map[String, Any], element: List[A]): Map[String, Any] =
        element.foldLeft(datum)((a, b) => addJsElement(a, b))
}

/**
 * Definition of a processor
 */
case class ProcessorDefinition(
    id: String,
    name: String,
    config: JsObject,
    resultName: String,
    next: List[String])

abstract class BufferProcessor(genActor: ActorRef, resultName: String) extends BaseProcessor(resultName: String) {}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]

    // Add our parent (the Router of this Routee) to cache
    Cache.getAs[collection.mutable.Map[ActorRef, ActorRef]]("router.mapping")
        .getOrElse(collection.mutable.Map[ActorRef, ActorRef]()) += self -> context.parent

    // Set up pipeline, either one that sends back the result, or one that just sinks
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    senderActor match {
        case Some(ref) => {
            // Set up enumeratee that sends the result back to sender
            val sendBackEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((dp: DataPacket) => {
                ref ! dp
                dp
            }) compose Enumeratee.onEOF(() => ref ! new StopPacket)
            processors.foreach(processor => enumerator |>> (processor compose sendBackEnumeratee) &>> sinkIteratee)
        }
        case None => processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    }

    def cleanup(sendEof: Boolean): Unit = {
        // Send message to the monitor actor
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
            self,
            "done")

        // Remove parent relationship from Cache
        Cache.getAs[collection.mutable.Map[ActorRef, ActorRef]]("router.mapping")
            .getOrElse(collection.mutable.Map[ActorRef, ActorRef]()) -= self

        if (sendEof)
            channel.eofAndEnd
        else
            channel.end
        //context.stop(self)
        self ! PoisonPill
    }

    def cleanup(): Unit = cleanup(true)

    def setup() = {}

    def _receive: PartialFunction[Any, Unit] = PartialFunction.empty

    def receive = _receive orElse {
        case ip: InitPacket              => setup
        case sp: StopPacket              => cleanup
        case a                           => play.api.Logger.warn(this.getClass.toString + " received an unhandled packet of type " + a.getClass.toString + ":\n" + a.toString)
    }
}

abstract class DataMerger() {
    def merge(packets: List[DataPacket]): DataPacket = ???
}

abstract class TuktuGlobal() {
    def onStart(app: Application): Unit = {}
    def onStop(app: Application): Unit = {}
}