package controllers.viz

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
import tuktu.viz.actor.GetEnumerator

object Charting extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def graphingEndPoint = WebSocket.tryAccept[String] { request =>
        // Ignore input sent back
        val in = Iteratee.ignore[String]
        
        // Get enumerator from charting actor
        val fut = (Akka.system.actorSelection("user/tuktu.viz.ChartingActor") ? new GetEnumerator()).asInstanceOf[Future[Enumerator[String]]]
        fut.map { out =>
            Right(in, out)
        }
    }
    
    def websocketJs = Action { implicit request =>
        Ok(views.html.viz.charts())
    }
}