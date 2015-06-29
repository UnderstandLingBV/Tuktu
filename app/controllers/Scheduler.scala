package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api._
import tuktu.utils.util
import play.api.libs.concurrent.Akka
import play.api.cache.Cache
import akka.util.Timeout

object Scheduler extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        
    def overview() = Action.async { implicit request =>
        // Get the monitor
        val fut = (Akka.system.actorSelection("user/TuktuScheduler") ? new Overview()).asInstanceOf[Future[List[String]]]
        fut.map(res =>
            Ok(views.html.scheduler.overview(
                    res,
                    util.flashMessagesToMap(request)
            ))
        )
    }
    
    def scheduleSimple() = Action { implicit request =>
        Ok(views.html.scheduler.simple(util.flashMessagesToMap(request)))
    }
    
    def scheduleCron() = Action { implicit request =>
        Ok(views.html.scheduler.cron(util.flashMessagesToMap(request)))
    }
    
    def scheduleSimpleStart() = Action { implicit request =>
        Redirect(routes.Scheduler.overview)
    }
    
    def scheduleCronStart() = Action { implicit request =>
        Redirect(routes.Scheduler.overview)
    }
    
    
}