package tuktu.http.generators

import tuktu.api._
import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill }
import akka.util.Timeout
import play.api.cache.Cache
import play.api.libs.json.{ JsValue, JsObject }
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Iteratee, Input }
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Gets a webpage's content based on REST request
 */
class TuktuJSGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    var api_endpoint: String = _
    var includes: List[JsObject] = _

    // Timeout from Cache
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore

    // Logging enumeratee
    def logEnumeratee[T] = Enumeratee.recover[T] {
        case (e, input) => System.err.println("Tuktu JS generator error happened on: " + input, e)
    }

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
                    case None    => sActor
                }
            }

            sourceActor ! d

            d
        })

        def runProcessor() = {
            Enumerator(dp) |>> (processors.head compose sendBackEnum compose logEnumeratee) &>> sinkIteratee
        }
    }

    override def receive() = {
        case ip: InitPacket => {}
        case sp: StopPacket => {
            val enum: Enumerator[DataPacket] = Enumerator.enumInput(Input.EOF)
            enum |>> (processors.head compose logEnumeratee) &>> sinkIteratee

            channel.eofAndEnd
            self ! PoisonPill
        }
        case config: JsValue => {
            // Get required config params to provide JS
            api_endpoint = (config \ "api_endpoint").asOpt[String].getOrElse("http://localhost:9000/web")
            includes = (config \ "includes").as[List[JsObject]]
        }
        case dp: DataPacket => {
            val referrer = dp.data(0)("referrer").asInstanceOf[String]

            // Find first regular expression to match the referrer
            includes.find(include => (include \ "page_regex").as[String].r.findFirstIn(referrer) != None) match {
                case None => {
                    // No captures defined for this page
                    val dp = new DataPacket(List(Map("referrer" -> referrer, "js" -> "// No captures defined for this page.")))

                    // Push to all async processors
                    channel.push(dp)

                    // Send through our enumeratee
                    val p = new senderReturningProcessor(sender, dp)
                    p.runProcessor()
                }
                case Some(include) => {
                    // Loop over all captures and extract identifier, xpath, selector and event to capture
                    val captures = (include \ "captures").as[List[JsObject]]

                    val cap = for (capture <- captures) yield {
                        val xpath = (capture \ "xpath").asOpt[Boolean].getOrElse(false)
                        val selector = (capture \ "selector").as[String]
                        val identifier = (capture \ "identifier").asOpt[String].getOrElse(selector)
                        val event = (capture \ "event").as[String]

                        (identifier, xpath, selector, event)
                    }

                    val dp = new DataPacket(List(Map("referrer" -> referrer, "js" -> views.js.Tuktu(api_endpoint, cap).toString)))

                    // Push to all async processors
                    channel.push(dp)

                    // Send through our enumeratee
                    val p = new senderReturningProcessor(sender, dp)
                    p.runProcessor()
                }
            }
        }
    }
}