package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.Play.current
import play.api.cache.Cache
import play.api.data._
import play.api.data.Forms._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.validation.Constraints._
import play.api.libs.concurrent.Akka
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.utils.util
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

object Scheduler extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // retrieve the TuktuScheduler
    val scheduler = Akka.system.actorSelection("user/TuktuScheduler") 
    
    // Show an overview of all active schedulers
    def overview() = Action.async { implicit request =>
        // Get the monitor
        val fut = (scheduler ? new Overview()).asInstanceOf[Future[List[String]]]
        fut.map(res =>
            Ok(views.html.scheduler.overview(
                    res,
                    util.flashMessagesToMap(request)
            ))
        )
    }
    
    // create a new simple scheduler
    def scheduleSimple() = Action { implicit request =>
        Ok(views.html.scheduler.simple(util.flashMessagesToMap(request)))
    }
    
    // create a new cron scheduler
    def scheduleCron() = Action { implicit request =>
        Ok(views.html.scheduler.cron(util.flashMessagesToMap(request)))
    }
    
    case class simpleSchedule(
        name: String,
        initialDelay: String,
        interval: String,
        id: String
    )
    
    val simpleScheduleForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1)),
            "initialDelay" -> text.verifying(nonEmpty, minLength(5)),
            "interval" -> text.verifying(nonEmpty, minLength(5)),
            "id" -> text.verifying(nonEmpty, minLength(1))
        ) (simpleSchedule.apply)(simpleSchedule.unapply)
    )
    
    // kick off a simple scheduler
    def scheduleSimpleStart() = Action { implicit request =>
        //Bind
        simpleScheduleForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            },
            job => {                
                val initialDelay = Duration(job.initialDelay).asInstanceOf[FiniteDuration]
                val interval = Duration(job.interval).asInstanceOf[FiniteDuration]
                val dispatchRequest = new DispatchRequest(job.id, None, false, false, false, None)     
                
                scheduler ! new SimpleScheduler(job.name, initialDelay, interval, dispatchRequest)
                
            } )
        Redirect(routes.Scheduler.overview)
    }
    
    case class cronSchedule(
        name: String,
        cronSchedule: String,        
        id: String
    )
    
    val cronScheduleForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1)),
            "cronSchedule" -> text.verifying(nonEmpty, minLength(5)),
            "id" -> text.verifying(nonEmpty, minLength(1))
        ) (cronSchedule.apply)(cronSchedule.unapply)
    )
    
    // kick off a cron scheduler    
    def scheduleCronStart() = Action { implicit request =>
        //Bind
        cronScheduleForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            },
            job => {                               
                val dispatchRequest = new DispatchRequest(job.id, None, false, false, false, None)     
                
                scheduler ! new CronScheduler(job.name, job.cronSchedule, dispatchRequest)
                
            } )
        Redirect(routes.Scheduler.overview)
    }    
   
    // terminate a schedule
    def terminate(name: String) = Action {
        scheduler ! new KillRequest(name)
        Redirect(routes.Scheduler.overview)
    }
    
}