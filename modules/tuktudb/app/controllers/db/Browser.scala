package controllers.db

import scala.Right
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorLogging
import akka.actor.ActorSelection.toScala
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.mvc.Action
import play.api.mvc.BodyParsers
import play.api.mvc.Controller
import tuktu.api.OverviewReply
import tuktu.api.OverviewRequest
import play.api.libs.concurrent.Akka
import tuktu.api.ContentRequest
import tuktu.api.ContentReply
import tuktu.api.utils
import play.api.libs.json.Json

object Browser extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    /**
     * Shows the main filebrowser
     */
    def index() = Action {
        Ok(views.html.db.browser())
    }
    
    /**
     * Fetch the buckets and their sizes
     */
    def mainOverview() = Action.async { implicit request =>
        // Fetch the bucket overview
        val fut = (Akka.system.actorSelection("user/tuktu.db.Daemon") ? new OverviewRequest(0)).asInstanceOf[Future[OverviewReply]]

        fut.map {
            case or: OverviewReply => {
                Ok(views.html.db.overview(or.bucketCounts))
            }
        }
    }
    
    /**
     * Gets a bucket's content
     */
    def getBucket() = Action.async { implicit request =>
        // Check which bucket
        val bucket = java.net.URLDecoder.decode(
                request.body.asFormUrlEncoded.get("key").head
        )
        
        // Fetch the bucket's content
        val fut = (Akka.system.actorSelection("user/tuktu.db.Daemon") ? new ContentRequest(bucket, 0)).asInstanceOf[Future[ContentReply]]
        
        fut.map {
            case cr: ContentReply => {
                // Convert everything to JSON
                val content = cr.data.map(d => Json.prettyPrint(utils.AnyToJsValue(d)))
                
                Ok(views.html.db.content(content, request.body.asFormUrlEncoded.get("idx").head.toInt))
            }
        }
    }
}