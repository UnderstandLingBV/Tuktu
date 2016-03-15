package controllers.web

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ActorRef
import java.net.URL
import java.nio.file._
import play.api.cache.Cache
import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json.{ Json, JsArray, JsObject, JsString }
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import tuktu.api.{ DataPacket, DispatchRequest }
import scala.concurrent.Future
import tuktu.api.WebJsObject
import tuktu.api.utils
import tuktu.api.WebJsNextFlow
import tuktu.web.js.JSGeneration
import tuktu.api.WebJsOrderedObject

object Application extends Controller {
    /**
     * Handles a polymorphic JS-request
     */
    def handleRequest(idOption: Option[String], referer: Option[String], request: Request[AnyContent], isInitial: Boolean): Future[Result] = {
        if (idOption.isEmpty && referer.isEmpty)
            Future { BadRequest("// No referrer found in HTTP headers.") }
        else {
            val id = idOption.getOrElse(referer.get)
            Play.current.configuration.getString("tuktu.webrepo") match {
                case None => Future { BadRequest("// No repository for JavaScripts and Tuktu flows set in Tuktu configuration.") }
                case Some(webRepo) => {
                    // Get the referer
                    val referrer = request.headers.get("referer")
                    // Get actual actor
                    val actorRefMap = Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap")
                        .getOrElse(collection.mutable.Map[String, ActorRef]())

                    // Check if JS actor is running
                    if (!actorRefMap.contains(id))
                        Future { BadRequest("// The analytics script is not enabled.") }
                    else {
                        implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
                        // Get body data and potentially the name of the next flow
                        val (bodyData, flowName) = {
                            val params = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]
                            (
                                (params \ "d").asOpt[JsObject].getOrElse(Json.obj()),
                                (params \ "f").asOpt[String])
                        }

                        // Set up the data packet
                        val dataPacket = if (isInitial)
                            new DataPacket(List(Map(
                                // By default, add referer, request and headers
                                "url" -> referrer.getOrElse(""),
                                "request" -> request,
                                "headers" -> request.headers,
                                Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field") -> new WebJsOrderedObject(List())
                            )))
                        else {
                            new DataPacket(List(Map(
                                // By default, add referer, request and headers
                                "url" -> referrer.getOrElse(""),
                                "request" -> request,
                                Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field") -> new WebJsOrderedObject(List()),
                                "headers" -> request.headers) ++ bodyData.keys.map(key => key -> utils.JsValueToAny(bodyData \ key))))
                        }

                        // See if we need to start a new flow or if we can send to the running actor
                        val resultFut = if (isInitial) {
                            // Send the Actor a DataPacket
                            val actorRef = actorRefMap(id)
                            actorRef ? dataPacket
                        } else {
                            // Since this is not the default flow, we have to see if this one is running, and start
                            // if if this is not the case

                            // Flow name must be set
                            flowName match {
                                case None => {
                                    // Flow name is gone, this cant be
                                    Future {}
                                }
                                case Some(fn) => {
                                    // See if the flow for this one is already running
                                    if (!actorRefMap.contains(id + "." + fn)) {
                                        // Dispatch new config
                                        val fut = Akka.system.actorSelection("user/TuktuDispatcher") ?
                                            new DispatchRequest(
                                                webRepo.drop(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs").size)
                                                    + "/" + id + "/" + fn, None, false, true, true, None)
                                        // We must wait here
                                        val actorRef = Await.result(fut, timeout.duration).asInstanceOf[ActorRef]
                                        // Add to our map
                                        Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap")
                                            .getOrElse(collection.mutable.Map[String, ActorRef]()) +=
                                            (id + "." + fn -> actorRef)
                                    }

                                    // Send the Actor a DataPacket containing the referrer
                                    actorRefMap(id + "." + fn) ? dataPacket
                                }
                            }
                        }

                        // Return result
                        resultFut.map {
                            case dp: DataPacket =>
                                // Get all the JS elements and output them one after the other
                                val jsResult = JSGeneration.PacketToJsBuilder(dp)
                                Ok(views.js.Tuktu(jsResult._2, jsResult._1,
                                    Play.current.configuration.getString("tuktu.url").get +
                                    Play.current.configuration.getString("tuktu.jsurl").get + idOption.map('/' + _).getOrElse(""),
                                    jsResult._3))
                            case _ =>
                                // Return blank
                                Ok("").as("text/javascript")
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads a JavaScript analytics script depending on referrer
     */
    def TuktuJs = Action.async { implicit request =>
        handleRequest(None, request.headers.get("referer") match {
            case None => None
            case Some(ref) => Some({
                val host = new URL(ref).getHost
                if (host.startsWith("www.")) host.drop("www.".length)
                else host
            })
        }, request, true)
    }

    /**
     * Invoke a flow based on a GET parameter that serves as ID
     */
    def TuktuJsGet(id: String) = Action.async { implicit request =>
        handleRequest(Some(id), None, request, true)
    }

    /**
     * Handles analytics by referrer
     */
    def web = Action.async { implicit request =>
        handleRequest(None, request.headers.get("referer") match {
            case None => None
            case Some(ref) => Some({
                val host = new URL(ref).getHost
                if (host.startsWith("www.")) host.drop("www.".length)
                else host
            })
        }, request, false)
    }

    /**
     * Handles analytics by id
     */
    def webGet(id: String) = Action.async { implicit request =>
        handleRequest(Some(id), None, request, false)
    }
}