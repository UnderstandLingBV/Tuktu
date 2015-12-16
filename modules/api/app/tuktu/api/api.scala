package tuktu.api

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.collection.GenTraversableOnce
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.cache.Cache
import play.api.Application

case class DataPacket(
        data: List[Map[String, Any]]
) extends java.io.Serializable {
    def isEmpty: Boolean = data.isEmpty
    def nonEmpty: Boolean = data.nonEmpty
    def filter(f: Map[String, Any] => Boolean): DataPacket = new DataPacket(data.filter(f))
    def filterNot(f: Map[String, Any] => Boolean): DataPacket = new DataPacket(data.filterNot(f))
    def map(f: Map[String, Any] => Map[String, Any]): DataPacket = new DataPacket(data.map(f))
    def flatMap(f: Map[String, Any] => GenTraversableOnce[Map[String, Any]]): DataPacket = new DataPacket(data.flatMap(f))
    def foreach(f: Map[String, Any] => Unit): Unit = data.foreach(f)
}

case class DispatchRequest(
        configName: String,
        config: Option[JsValue],
        isRemote: Boolean,
        returnRef: Boolean,
        sync: Boolean,
        sourceActor: Option[ActorRef]
)

case class InitPacket()

case class StopPacket()

case class ResponsePacket(
        json: JsValue
)

case class HealthCheck()
case class HealthReply()

case class ClusterNode(
        host: String,
        akkaPort: Int,
        UIPort: Int
)

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
        timestamp: Long = System.currentTimeMillis
)

case class MonitorOverviewRequest()
case class MonitorOverviewResult(
        runningJobs: Map[String, AppMonitorObject],
        finishedJobs: Map[String, AppMonitorObject],
        subflows: Map[String, String]
)

case class MonitorLastDataPacketRequest(
        flow_name: String,
        processor_id: String
)

case class ProcessorMonitorPacket(
        typeOf: MPType,
        uuid: String,
        processor_id: String,
        data: DataPacket,
        timestamp: Long = System.currentTimeMillis
)

case class AppMonitorObject(
        uuid: String,
        instances: Int,
        startTime: Long,
        var finished_instances: Int = 0,
        var endTime: Option[Long] = None,
        expirationTime: Long = play.api.Play.current.configuration.getLong("tuktu.monitor.finish_expiration").getOrElse(30L) * 60 * 1000,
        errorExpirationTime: Long = play.api.Play.current.configuration.getLong("tuktu.monitor.error_expiration").getOrElse(40320L) * 60 * 1000,
        errors: collection.mutable.Map[String, String] = collection.mutable.Map.empty,
        actors: collection.mutable.Set[ActorRef] = collection.mutable.Set.empty,
        flowDataPacketCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        flowDatumCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorDataPackets: collection.mutable.Map[String, collection.mutable.Map[MPType, DataPacket]] = collection.mutable.Map.empty,
        processorDataPacketCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorDatumCount: collection.mutable.Map[String, collection.mutable.Map[MPType, Int]] = collection.mutable.Map.empty,
        processorBeginTimes: collection.mutable.Map[String, collection.mutable.Queue[Long]] = collection.mutable.Map.empty,
        processorDurations: collection.mutable.Map[String, collection.mutable.ListBuffer[Long]] = collection.mutable.Map.empty
) {
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
        val timestamp: Long = System.currentTimeMillis
) {
    def getName = actor.path.toStringWithoutAddress
    def getParentName = actor.path.parent.toStringWithoutAddress 
}
case class AppMonitorUUIDPacket(
        uuid: String,
        status: String,
        timestamp: Long = System.currentTimeMillis
)

case class AddMonitorEventListener()
case class RemoveMonitorEventListener()

case class AppInitPacket(
        uuid: String,
        instanceCount: Int,
        mailbox: Option[ActorRef] = None,
        timestamp: Long = System.currentTimeMillis
)
case class AppStopPacket(
        uuid: String,
        timestamp: Long = System.currentTimeMillis
)
case class SubflowMapPacket(
        mailbox: ActorRef,
        subflows: List[ActorRef]
)
case class ErrorNotificationPacket(
        uuid: String,
        configName: String,
        processorName: String,
        input: String,
        error: Throwable
)
case class ClearFlowPacket(
    uuid: String
)
/**
 * End monitoring stuff
 */

abstract class BaseProcessor(resultName: String) {
    def initialize(config: JsObject): Unit = {}
    def processor(): Enumeratee[DataPacket, DataPacket] = ???
}

/**
 * Definition of a processor
 */
case class ProcessorDefinition(
        id: String,
        name: String,
        config: JsObject,
        resultName: String,
        next: List[String]
)

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
            val sendBackEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
                ref ! dp
                dp
            })
            processors.foreach(processor => enumerator |>> (processor compose sendBackEnumeratee) &>> sinkIteratee)
        }
        case None => processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    }

    def cleanup() = {
        // Send message to the monitor actor
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                self,
                "done"
        )

        // Remove parent relationship from Cache
        Cache.getAs[collection.mutable.Map[ActorRef, ActorRef]]("router.mapping")
            .getOrElse(collection.mutable.Map[ActorRef, ActorRef]()) -= self

        channel.eofAndEnd
        //context.stop(self)
        self ! PoisonPill
    }

    def setup() = {}

    def receive() = {
        case ip: InitPacket => setup
        case config: JsValue => ???
        case sp: StopPacket => cleanup
        case _ => {}
    }
}

abstract class DataMerger() {
    def merge(packets: List[DataPacket]): DataPacket = ???
}

abstract class TuktuGlobal() {
    def onStart(app: Application): Unit = {}
    def onStop(app: Application): Unit = {}
}