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
                            val resultFut = actorRef ? new DataPacket(List(Map("referrer" -> referrer)))
                            val result = Await.result(resultFut, timeout.duration).asInstanceOf[DataPacket]

                            // Take js from result
                            Ok(result.data(0)("js").asInstanceOf[String]).as("text/javascript")
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
            val result = request.body.asText match {
                case None      => new DataPacket(Nil)
                case Some(str) => new DataPacket(List(tuktu.api.utils.JsObjectToMap(Json.parse(str).as[JsObject])))
            }
            Ok
        }
    }
}