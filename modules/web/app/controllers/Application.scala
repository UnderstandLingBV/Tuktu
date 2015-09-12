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

object Application extends Controller {
    /**
     * Turns a data packet into javascript that can be executed and returned by Tuktu
     */
    def PacketToJsBuilder(dp: DataPacket): String = {
        (for {
            datum <- dp.data
            (dKey, dValue) <- datum
            
            if (dValue match {
                case a: WebJsObject => true
                case _ => false
            })
        } yield {
            // Get the value to obtain and place it in a key with a proper name that we will collect
            "var " + dKey + " = " + dValue.asInstanceOf[WebJsObject].js match {
                case v: String => "'" + v + "'"
                case v: Any => v
            }
        }).toList.mkString(";")
    }
    
    /**
     * Loads a JavaScript analytics script depending on referrer
     */
    def TuktuJs = Action.async { implicit request =>
        request.headers.get("referer") match {
            case None => Future { BadRequest("// No referrer found in HTTP headers.") }
            case Some(referrer) => {
                Play.current.configuration.getString("tuktu.webrepo") match {
                    case None => Future { BadRequest("// No repository for JavaScripts and Tuktu flows set in Tuktu configuration.") }
                    case Some(webRepo) => {
                        // Convert referrer to URL to get its host
                        val url = new URL(referrer)
                        // Get actual actor
                        val actorRefMap = Cache.getAs[Map[String, ActorRef]]("web.hostmap").getOrElse(Map[String, ActorRef]())

                        // Check if JS actor is running
                        if (!actorRefMap.contains(url.getHost))
                            Future { BadRequest("// The analytics script is not enabled.") }
                        else {
                            implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

                            // Send the Actor a DataPacket
                            val actorRef = actorRefMap(url.getHost)
                            val resultFut = actorRef ? new DataPacket(List(Map(
                                    // By default, add referer, request and headers
                                    "referer" -> referrer,
                                    "request" -> request,
                                    "headers" -> request.headers
                            )))
                            resultFut.map {
                                case dp: DataPacket => {
                                    // Get all the JS elements and output them one after the other
                                    Ok(PacketToJsBuilder(dp)).as("text/javascript")
                                }
                                case _ => {
                                    // Return blank
                                    Ok("").as("text/javascript")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles analytics
     * @TODO: Actually this should start a new flow?
     */
    def web = Action.async { implicit request =>
        request.headers.get("referer") match {
            case None => Future { BadRequest("// No referrer found in HTTP headers.") }
            case Some(referrer) => {
                request.body.asFormUrlEncoded match {
                    case Some(bodyData) => {
                        implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
                        
                        // Convert referrer to URL to get its host
                        val url = new URL(referrer)
                        // Get actual actor
                        val actorRefMap = Cache.getAs[Map[String, ActorRef]]("web.hostmap").getOrElse(Map[String, ActorRef]())
                        
                        // Get the data from the body
                        val dp = new DataPacket(List(Map(
                                // By default, add referer, request and headers
                                "referer" -> referrer,
                                "request" -> request,
                                "headers" -> request.headers
                        ) ++ bodyData.map(elem => elem._1 -> elem._2.head)))
                        
                        // Send the Actor the data packet
                        val actorRef = actorRefMap(url.getHost)
                        val resultFut = actorRef ? dp
                        resultFut.map {
                            case dp: DataPacket => {
                                // Get all the JS elements and output them one after the other
                                Ok(PacketToJsBuilder(dp)).as("text/javascript")
                            }
                            case _ => {
                                // Return blank
                                Ok("").as("text/javascript")
                            }
                        }
                    }
                    case None => Future { BadRequest("// Could not find body.") }
                }
            }
        }
    }
}