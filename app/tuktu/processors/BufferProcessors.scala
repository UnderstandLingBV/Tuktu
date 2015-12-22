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
import play.api.libs.json.JsObject
import play.api.cache.Cache
import scala.collection.mutable.ListBuffer
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Enumerator

/**
 * This actor is used to buffer stuff in
 */
class BufferActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val buffer = collection.mutable.ListBuffer[Map[String, Any]]()

    // Send init packet to the remote generator
    remoteGenerator ! new InitPacket

    def receive() = {
        case "release" => {
            // Create datapacket and clear buffer
            val dp = new DataPacket(buffer.toList.asInstanceOf[List[Map[String, Any]]])
            buffer.clear

            // Push forward to remote generator
            remoteGenerator ! dp

            sender ! "ok"
        }
        case sp: StopPacket => {
            remoteGenerator ! sp
            self ! PoisonPill
        }
        case item: Map[String, Any] => {
            buffer += item
            sender ! "ok"
        }
    }
}

/**
 * Buffers datapackets until we have a specific amount of them
 */
class SizeBufferProcessor(resultName: String) extends BaseProcessor(resultName) {
    var maxSize: Int = _

    override def initialize(config: JsObject) {
        maxSize = (config \ "size").as[Int]
    }

    // Iteratee to take the data we need
    def groupPackets: Iteratee[DataPacket, DataPacket] = for (
            dps <- Enumeratee.take[DataPacket](maxSize) &>> Iteratee.getChunks
    ) yield new DataPacket(dps.flatMap(data => data.data))

    // Use the iteratee and Enumeratee.grouped
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.grouped(groupPackets)
}

/**
 * Buffers datapackets for a given amount of time and then releases the buffer for processing
 */
class TimeBufferProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var interval = -1
    var cancellable: Cancellable = null

    // Set up the buffering actor
    val bufferActor = Akka.system.actorOf(Props(classOf[BufferActor], genActor))

    override def initialize(config: JsObject) {
        interval = (config \ "interval").as[Int]

        // Schedule periodic release
        cancellable =
            Akka.system.scheduler.schedule(interval milliseconds,
                interval milliseconds,
                bufferActor,
                "release")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Iterate over our data and add to the buffer
        data.data.foreach(datum => bufferActor ! datum)

        Future { data }
    }) compose Enumeratee.onEOF(() => {
        cancellable.cancel
        Await.result(bufferActor ? "release", Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        bufferActor ! new StopPacket
    })
}

/**
 * Buffers until EOF (end of data stream) is found
 */
class EOFBufferProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Iteratee to take the data we need
    def groupPackets: Iteratee[DataPacket, DataPacket] = for (
            dps <- Enumeratee.takeWhile[DataPacket](_ != Input.EOF) &>> Iteratee.getChunks
    ) yield new DataPacket(dps.flatMap(data => data.data))

    // Use the iteratee and Enumeratee.grouped
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.grouped(groupPackets)
}

/**
 * Buffers and Groups data
 */
class GroupByBuffer(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        // Get the field to group on
        fields = (config \ "fields").as[List[String]]
    }

    // Iteratee that acts like EOF buffer
    def groupPackets: Iteratee[DataPacket, List[Map[String, Any]]] = for (
            dps <- Enumeratee.takeWhile[DataPacket](_ != Input.EOF) &>> Iteratee.getChunks
    ) yield dps.flatMap(data => data.data)

    // Group the data by keys
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.grouped(groupPackets) compose
        Enumeratee.mapFlatten((data: List[Map[String, Any]]) => {
            Enumerator.enumerate(
                    for (d <- data.groupBy(datum => fields.map(field => datum(field))).values) yield
                        new DataPacket(d)
            )
        })
}

/**
 * Hybrid processor that either buffers data until a signal is received, or sends the signal.
 * This means that you MUST always have 2 instances of this processor active, in separate
 * branches.
 */
class SignalBufferProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Set up the buffering actor
    var bufferActor: ActorRef = _
    var signaller = false

    override def initialize(config: JsObject) {
        // Get name of bufferer
        val bufferName = (config \ "signal_name").as[String]
        // Remote or not?
        val node = (config \ "node").asOpt[String]
        // Signaller or signalee?
        signaller = (config \ "is_signaller").as[Boolean]

        if (signaller) {
            // This is the signaller, we need to fetch the remote actor
            node match {
                case Some(n) => {
                    // Get it from a remote location
                    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                    val fut = Akka.system.actorSelection("akka.tcp://application@" + n + ":" + clusterNodes(n).akkaPort + "/user/SignalBufferActor_" + bufferName) ? Identify(None)
                    bufferActor = Await.result(fut.mapTo[ActorIdentity], timeout.duration).getRef
                }
                case None => {
                    // It has be alive locally
                    val fut = Akka.system.actorSelection("user/SignalBufferActor_" + bufferName) ? Identify(None)
                    bufferActor = Await.result(fut.mapTo[ActorIdentity], timeout.duration).getRef
                }
            }
        } else {
            // This is not the signaller, we need to keep track of the data
            bufferActor = Akka.system.actorOf(Props(classOf[SignalBufferActor], genActor), name = "SignalBufferActor_" + bufferName)
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Send this packet as-is to our bufferer if we are not the signaller
        if (!signaller) {
            val future = bufferActor ? data
            future.map {
                case _ => data
            }
        } else Future { data }
    }) compose Enumeratee.onEOF(() => {
        // See if we are the signaller or not
        if (signaller) {
            Await.result(bufferActor ? "release", Cache.getAs[Int]("timeout").getOrElse(5) seconds)
            bufferActor ! new StopPacket
        }
    })
}

/**
 * Buffer actor to go with the signal buffer processor
 */
class SignalBufferActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val buffer = collection.mutable.ListBuffer[DataPacket]()

    remoteGenerator ! new InitPacket

    def receive() = {
        case "release" => {
            // Send data packets to remote generator one by one
            buffer.foreach(dp => remoteGenerator ! dp)

            // Clear buffer
            buffer.clear
            sender ! "ok"
        }
        case sp: StopPacket => {
            remoteGenerator ! sp
            self ! PoisonPill
        }
        case dp: DataPacket => {
            buffer += dp
            sender ! "ok"
        }
    }
}

/**
 * Splits the elements of a single data packet into separate data packets (one per element)
 */
class DataPacketSplitterProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Split the data from our DataPacket into separate data packets
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapFlatten((data: DataPacket) => {
        Enumerator.enumerate(
                for (datum <- data.data) yield
                    new DataPacket(List(datum))
        )
    })
}