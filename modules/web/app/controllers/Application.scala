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

object Application extends Controller {
    /**
     * Loads a JavaScript analytics script depending on referrer
     */
    def TuktuJs = Action { implicit request =>
        request.headers.get("referer") match {
            case None => BadRequest("// No referrer found in HTTP headers.")
            case Some(referrer) => {
                Play.current.configuration.getString("tuktu.webrepo") match {
                    case None => BadRequest("// No repository for JavaScripts and Tuktu flows set in Tuktu configuration.")
                    case Some(webRepo) => {
                        // Convert referrer to URL to get its host
                        val url = new URL(referrer)

                        // Check if host has folder and JavaScript file in web repo
                        if (!Files.exists(Paths.get(webRepo, url.getHost)))
                            BadRequest("// The referrer has no entry in the repository yet.")
                        else if (!Files.exists(Paths.get(webRepo, url.getHost, "Tuktu.json")))
                            BadRequest("// The referrer has no analytics script defined yet.")
                        else {
                            implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

                            // Open file and parse Json
                            val configFile = scala.io.Source.fromFile(Paths.get(webRepo, url.getHost, "Tuktu.json").toFile, "utf-8")
                            val cfg = Json.parse(configFile.mkString).as[JsObject]
                            configFile.close

                            // Dispatch new config
                            val fut = Akka.system.actorSelection("user/TuktuDispatcher") ?
                                new DispatchRequest("", Some(cfg), false, true, true, None)
                            val actorRef = Await.result(fut, timeout.duration).asInstanceOf[ActorRef]

                            // Send the Actor a DataPacket containing the referrer
                            val resultFut = actorRef ? referrer
                            val js = Await.result(resultFut, timeout.duration).asInstanceOf[String]

                            // Take js from result
                            Ok(js).as("text/javascript")
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