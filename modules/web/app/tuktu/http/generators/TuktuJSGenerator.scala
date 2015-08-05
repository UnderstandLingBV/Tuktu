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
class TuktuJSGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var api_endpoint: String = _
    var includes: List[JsObject] = _

    override def receive() = {
        case ip: InitPacket => setup
        case sp: StopPacket => cleanup
        case config: JsValue => {
            // Get required config params to provide JS
            api_endpoint = (config \ "api_endpoint").asOpt[String].getOrElse("http://localhost:9000/web")
            includes = (config \ "includes").as[List[JsObject]]
        }
        case referrer: String => {
            // Find first regular expression to match the referrer and create javascript based on it
            val js = includes.find(include => (include \ "page_regex").as[String].r.findFirstIn(referrer) != None) match {
                case None => {
                    "// No captures defined for this page."
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

                    views.js.Tuktu(self.path.toString, api_endpoint, cap).toString
                }
            }

            // Send back to source / sender
            val sourceActor = {
                senderActor match {
                    case Some(a) => a
                    case None    => sender
                }
            }

            sourceActor ! js
        }
        case dp: DataPacket => {
            // Just forward DataPackets
            channel.push(dp)
        }
    }
}