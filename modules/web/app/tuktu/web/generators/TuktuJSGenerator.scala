package tuktu.web.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.{ ActorRef, PoisonPill }
import akka.actor.PoisonPill
import akka.util.Timeout
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Input, Iteratee }
import play.api.libs.json.{ JsObject, JsValue }
import play.api.Play
import play.api.Play.current
import tuktu.api._

/**
 * Gets a webpage's content based on REST request
 */
class TuktuJSGenerator(
        resultName: String,
        processors: List[Enumeratee[DataPacket, DataPacket]],
        senderActor: Option[ActorRef]) extends TuktuBaseJSGenerator(resultName, processors, senderActor) {

    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Channeling (for asynchronous pipelines)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore[DataPacket]

    // Every processor but the first gets treated as asynchronous
    for (processor <- processors.tail)
        enumerator |>> processor &>> Iteratee.ignore[DataPacket]

    // Keep track of requesters that need to be notified of errors
    val requesters = collection.concurrent.TrieMap.empty[ActorRef, ActorRef]

    // Options
    var add_ip: Boolean = _

    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class SenderReturningProcessor(sActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((d: DataPacket) => {
            val sourceActor = senderActor match {
                case Some(a) => a
                case None    => sActor
            }

            sourceActor ! d
            // Remove this requester from the list.
            requesters -= sourceActor

            d
        })

        def runProcessor = {
            Enumerator(dp) |>> (processors.head compose sendBackEnum) &>> sinkIteratee
        }
    }

    def receive() = {
        case ip: InitPacket => {}
        case config: JsValue =>
            add_ip = (config \ "add_ip").asOpt[Boolean].getOrElse(false)

        case error: ErrorPacket =>
            // Inform all the requesters that an error occurred.
            requesters.foreach { _._1 ! error }
            requesters.clear

        case sp: StopPacket =>
            // Send message to the monitor actor
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(self, "done")

            val enum: Enumerator[DataPacket] = Enumerator.enumInput(Input.EOF)
            enum |>> processors.head &>> sinkIteratee

            channel.eofAndEnd
            self ! PoisonPill

        case RequestPacket(request, isInitial) =>
            requesters += sender -> sender

            // Get body data and potentially the name of the next flow
            val bodyData = request.body.asJson.flatMap {
                js => (js \ "d").asOpt[JsObject]
            }.getOrElse(JsObject(Nil))

            // Set up the data packet
            val dp = DataPacket(List(Map(
                // By default, add referer, request and headers
                "headers" -> request.headers,
                "cookies" -> request.cookies.map(c => c.name -> c.value).toMap,
                Cache.getAs[String]("web.jsname").getOrElse(Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field")) -> new WebJsOrderedObject(List()))
                ++ {
                    if (isInitial) {
                        Map.empty
                    } else {
                        bodyData.keys.map(key => key -> utils.JsValueToAny(bodyData \ key))
                    }
                }
                ++
                Seq(
                    if (add_ip) {
                        Some("request_remoteAddress" -> request.remoteAddress)
                    } else {
                        None
                    }).flatten))

            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new SenderReturningProcessor(sender, dp)
            p.runProcessor
    }
}