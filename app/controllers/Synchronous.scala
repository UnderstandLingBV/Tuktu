package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json.JsObject
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api.DataPacket
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.libs.concurrent.Promise
import tuktu.api.utils

object Synchronous extends Controller {
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    case class TimeoutPacket()

    /**
     * Invokes a JSON config and sends a piece of data to the resulting generator actor, returns a JSON field as result
     */
    def loadJsonPost() = Action.async { implicit request =>
        // Dispatch for this user, with config given
        val jsonBody = request.body.asJson.getOrElse(null)
        if (jsonBody != null) {
            // Get the ID from the request, we always use 1 instance
            val id = (jsonBody \ "id").as[String]
            // Optional custom timeout?
            val customTimeout = (jsonBody \ "timeout").asOpt[Int] match {
                case Some(t) => {
                    // Set the timeout
                    timeout = Timeout((t + 1) seconds)
                    Duration(t, "seconds")
                }
                case _ => timeout.duration
            }

            // Invoke the flow and wait for a reply
            val generator = Await.result(
                    Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest(id, None, false, true, true, None),
                    customTimeout
            ).asInstanceOf[ActorRef]
            
            // Forward data to generator and fetch result
            val resultFuture = (generator ? new DataPacket(List(utils.anyJsonToMap((jsonBody \ "body").as[JsObject])))).asInstanceOf[Future[DataPacket]]
            val timeoutFuture = Promise.timeout(TimeoutPacket, customTimeout)
            Future.firstCompletedOf(Seq(resultFuture, timeoutFuture)).map {
                case dp: DataPacket => {
                    // Only makes sense if we get one result
                    val packet = dp.data.head
                    
                    // Read the responding field
                    val fieldName = (jsonBody \ "field").as[String]
                    Ok(Json.obj(fieldName -> Json.parse(packet(fieldName).toString)))
                }
                case t: TimeoutPacket => InternalServerError(Json.obj(
                        "error" -> "Flow timed out during execution."
                ))
                case t: Any => InternalServerError(Json.obj(
                        "error" -> "Error during execution of flow.",
                        "description" -> t.toString
                ))
            }
        } else Future(BadRequest(
            Json.obj("error" -> "No JSON body given!")))
    }
}