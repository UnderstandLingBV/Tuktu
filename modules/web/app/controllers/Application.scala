package controllers.web

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ActorRef
import java.net.URL
import java.nio.file._
import play.api.cache.Cache
import play.api._
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
import tuktu.api.ErrorPacket
import tuktu.api.RequestPacket

object Application extends Controller {
    val byteArray = Files.readAllBytes(Paths.get("public", "images", "pixel.gif"))

    /**
     * Creates tracking cookies if web.set_cookies is true and they aren't set yet
     */
    def cookies(implicit request: Request[AnyContent]): List[Cookie] =
        if (Cache.getAs[Boolean]("web.set_cookies").getOrElse(true))
            List(
                request.cookies.get("t_s_id") match {
                    case None => Some(Cookie("t_s_id", java.util.UUID.randomUUID.toString))
                    case _    => None
                },
                request.cookies.get("t_u_id") match {
                    case None => Some(Cookie("t_u_id", java.util.UUID.randomUUID.toString, Some(Int.MaxValue)))
                    case _    => None
                }).flatten
        else Nil

    /**
     * Handles a polymorphic JS-request
     */
    def handleRequest(id: String, isGET: Boolean)(implicit request: Request[AnyContent]): Future[Result] =
        // Try to get actual actor from hostmap
        Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap").flatMap { _.get(id) } match {
            case None => Future { BadRequest("// The analytics script is not enabled.") }
            case Some(actorRef) =>
                implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

                // Forward request and handle result
                (actorRef ? RequestPacket(request, isGET)).map {
                    case dp: DataPacket =>
                        // Get all the JS elements and output them one after the other
                        val jsResult = JSGeneration.PacketToJsBuilder(dp)
                        Ok(views.js.Tuktu(jsResult._2, jsResult._1,
                            Cache.getOrElse[String]("web.url")("http://localhost:9000") + routes.Application.TuktuJsPost(id).url,
                            jsResult._3))
                            .withCookies(cookies: _*)
                    case error: ErrorPacket =>
                        BadRequest("// Internal error occured.")
                    case _ =>
                        // Return blank
                        Ok("").as("text/javascript")
                }
        }

    /**
     * Invoke a flow based on a GET parameter that serves as ID
     */
    def TuktuJsGet(id: String) = Action.async { implicit request =>
        handleRequest(id, true)
    }

    /**
     * Handles analytics by id
     */
    def TuktuJsPost(id: String) = Action.async { implicit request =>
        handleRequest(id, false)
    }

    /**
     * Serves an image instead of some JS
     */
    def imgGet(id: String) = Action.async { implicit request =>
        Future {
            // Try to get actual actor from hostmap
            Cache.getAs[collection.mutable.Map[String, ActorRef]]("web.hostmap").flatMap { _.get(id) } match {
                case None => Ok(byteArray).as("image/gif")
                case Some(actorRef) =>
                    // Send the Actor a DataPacket
                    actorRef ! RequestPacket(request, true)

                    // Return result
                    Ok(byteArray).as("image/gif").withCookies(cookies: _*)
            }
        }
    }
}