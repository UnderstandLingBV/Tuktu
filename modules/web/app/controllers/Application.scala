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

                        // Check if host has folder and JavaScript file in web repo
                        if (!Files.exists(Paths.get(webRepo, url.getHost)))
                            Future { BadRequest("// The referrer has no entry in the repository yet.") }
                        else if (!Files.exists(Paths.get(webRepo, url.getHost, "Tuktu.json")))
                            Future { BadRequest("// The referrer has no analytics script defined yet.") }
                        else if (!actorRefMap.contains(url.getHost))
                            Future { BadRequest("// The analytics script is not enabled.") }
                        else {
                            implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

                            // Send the Actor a DataPacket containing the referrer
                            val actorRef = actorRefMap(url.getHost)
                            val resultFut = actorRef ? referrer
                            resultFut.map {
                                case dp: DataPacket => {
                                    // Get all the JS elements and output them one after the other
                                    Ok(
                                        (for {
                                            datum <- dp.data
                                            (dKey, dValue) <- datum
                                            
                                            if (dValue match {
                                                case a: WebJsObject => true
                                                case _ => false
                                            })
                                        } yield dValue.asInstanceOf[WebJsObject]).toList.mkString(" ")
                                    ).as("text/javascript")
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
     */
    def web = Action { implicit request =>
        {
            request.body.asText match {
                case Some(str) => {
                    // Parse JSON, get path, and send data to respective actor
                    val map = tuktu.api.utils.JsObjectToMap(Json.parse(str).as[JsObject])
                    Akka.system.actorSelection(map("path").asInstanceOf[String]) ! new DataPacket(List(map - "path"))
                    Ok
                }
                case None => BadRequest
            }
        }
    }
}