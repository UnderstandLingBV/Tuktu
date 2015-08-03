package controllers.viz

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.WebSocket
import tuktu.viz.actor.EnumeratorReply
import tuktu.viz.actor.GetChartReply
import tuktu.viz.actor.GetChartRequest
import tuktu.viz.actor.GetEnumerator

object Charting extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def websocket(name: String) = WebSocket.tryAccept[String] { request =>
        // Ignore input sent back
        val in = Iteratee.ignore[String]
        
        // Get enumerator from charting actor
        val fut = (Akka.system.actorSelection("user/tuktu.viz.ChartingActor") ? new GetChartRequest(
                name, 0L, false, false
        )).asInstanceOf[Future[GetChartReply]]
        val reply = Await.result(fut, timeout.duration)
        
        // Stream out data
        val enumFut = (reply.actor ? new GetEnumerator()).asInstanceOf[Future[EnumeratorReply]]
        enumFut.map { out =>
            Right(in, {
                if (reply.historical)
                    Enumerator.enumerate(out.history) andThen out.enumerator
                else out.enumerator
            })
        }
    }
    
    def graphingEndPoint(name: String) = Action { implicit request =>
        Ok(views.html.viz.charts(name))
    }
}