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

object Application extends Controller {
    /**
     * Turns a data packet into javascript that can be executed and returned by Tuktu
     */
    def PacketToJsBuilder(dp: DataPacket): (String, Option[String]) = {
        var nextFlow: Option[String] = None
        
        val res = (for {
            datum <- dp.data
            (dKey, dValue) <- datum
            
            if (dValue match {
                case a: WebJsObject => true
                case a: WebJsNextFlow => {
                    // Ugly, but we have to do a side-effect here :)
                    nextFlow = Some(a.flowName)
                    false
                }
                case _ => false
            })
        } yield {
            // Get the value to obtain and place it in a key with a proper name that we will collect
            "tuktuvars." + dKey + " = " + (dValue.asInstanceOf[WebJsObject].js match {
                case v: String => "'" + v + "'"
                case v: Int => v.toString
            })
        }).toList.mkString(";")
        
        (res, nextFlow)
    }
    
    /**
     * Handles a polymorphic JS-request
     */
    def handleRequest(request: Request[AnyContent], isInitial: Boolean): Future[Result] = {
        request.headers.get("referer") match {
            case None => Future { BadRequest("// No referrer found in HTTP headers.") }
            case Some(referrer) => {
                Play.current.configuration.getString("tuktu.webrepo") match {
                    case None => Future { BadRequest("// No repository for JavaScripts and Tuktu flows set in Tuktu configuration.") }
                    case Some(webRepo) => {
                        // Convert referrer to URL to get its host
                        val url = new URL(referrer)
                        // Get actual actor
                        val actorRefMap = Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap")
                            .getOrElse(collection.mutable.Map[String, ActorRef]())

                        // Check if JS actor is running
                        if (!actorRefMap.contains(url.getHost))
                            Future { BadRequest("// The analytics script is not enabled.") }
                        else {
                            implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
                            // Get body data and potentially the name of the next flow
                            val (bodyData, flowName) = {
                                val params = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]
                                (
                                        (params \ "d").asOpt[JsObject].getOrElse(Json.obj()),
                                        (params \ "f").asOpt[String]
                                )
                            }
                            
                            // Set up the data packet
                            val dataPacket = if (isInitial)
                                new DataPacket(List(Map(
                                    // By default, add referer, request and headers
                                    "referer" -> referrer,
                                    "request" -> request,
                                    "headers" -> request.headers
                                )))
                            else
                                new DataPacket(List(Map(
                                    // By default, add referer, request and headers
                                    "referer" -> referrer,
                                    "request" -> request,
                                    "headers" -> request.headers
                                ) ++ bodyData.keys.map(key => key -> utils.JsValueToAny(bodyData \ key))))

                            // See if we need to start a new flow or if we can send to the running actor
                            val resultFut = if (isInitial) {
                                // Send the Actor a DataPacket
                                val actorRef = actorRefMap(url.getHost)
                                actorRef ? dataPacket
                            }
                            else {
                                // Since this is not the default flow, we have to see if this one is running, and start
                                // if if this is not the case
                                
                                // Flow name must be set
                                flowName match {
                                    case None => {
                                        // Flow name is gone, this cant be
                                        Future { }
                                    }
                                    case Some(fn) => {
                                        // See if the flow for this one is already running
                                        if (!actorRefMap.contains(url.getHost + "." + fn)) {
                                            // Dispatch new config
                                            val fut = Akka.system.actorSelection("user/TuktuDispatcher") ?
                                                new DispatchRequest(
                                                        webRepo.drop(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs").size)
                                                            + "/" + url.getHost + "/" + fn, None, false, true, true, None)
                                            // We must wait here
                                            val actorRef = Await.result(fut, timeout.duration).asInstanceOf[ActorRef]
                                            // Add to our map
                                            Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap")
                                                .getOrElse(collection.mutable.Map[String, ActorRef]()) +=
                                                (url.getHost + "." + fn -> actorRef)
                                        }
                                        
                                        // Send the Actor a DataPacket containing the referrer
                                        actorRefMap(url.getHost + "." + fn) ? dataPacket
                                    }
                                }
                            }
                            
                            // Return result
                            resultFut.map {
                                case dp: DataPacket =>
                                    // Get all the JS elements and output them one after the other
                                    val jsResult = PacketToJsBuilder(dp)
                                    Ok(views.js.Tuktu(jsResult._2, jsResult._1,
                                            Play.current.configuration.getString("tuktu.jsurl").getOrElse("/Tuktu.js")))
                                case _ =>
                                    // Return blank
                                    Ok("").as("text/javascript")
                            }
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
        handleRequest(request, true)
    }

    /**
     * Handles analytics
     */
    def web = Action.async { implicit request =>
        handleRequest(request, false)
    }
}