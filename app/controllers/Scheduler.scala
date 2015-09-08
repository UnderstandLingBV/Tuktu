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
import tuktu.api.DispatchRequest
import tuktu.utils.util
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import java.io.File

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
        jobs: List[String]
    )
    
    val simpleScheduleForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1)),
            "initialDelay" -> text.verifying(nonEmpty, minLength(5)),
            "interval" -> text.verifying(nonEmpty, minLength(5)),
            "jobs" -> list(text)
        ) (simpleSchedule.apply)(simpleSchedule.unapply)
    )
    
    // kick off a simple scheduler
    def scheduleSimpleStart() = Action { implicit request =>
        //Bind
        simpleScheduleForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            },
            jobsForm => {
                jobsForm.jobs.foreach(job => {
                    val initialDelay = Duration(jobsForm.initialDelay).asInstanceOf[FiniteDuration]
                    val interval = Duration(jobsForm.interval).asInstanceOf[FiniteDuration]
                    val dispatchRequest = new DispatchRequest(job, None, false, false, false, None)     
                    
                    scheduler ! new SimpleScheduler(jobsForm.name, initialDelay, interval, dispatchRequest)
                })
            } )
        Redirect(routes.Scheduler.overview)
    }
    
    case class cronSchedule(
        name: String,
        cronSchedule: String,        
        jobs: List[String]
    )
    
    val cronScheduleForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1)),
            "cronSchedule" -> text.verifying(nonEmpty, minLength(5)),
            "jobs" -> list(text)
        ) (cronSchedule.apply)(cronSchedule.unapply)
    )
    
    // kick off a cron scheduler    
    def scheduleCronStart() = Action { implicit request =>
        //Bind
        cronScheduleForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            },
            jobsForm => {
                jobsForm.jobs.foreach(job => {
                    val dispatchRequest = new DispatchRequest(job, None, false, false, false, None)     
                    scheduler ! new CronScheduler(jobsForm.name, jobsForm.cronSchedule, dispatchRequest)
                })
            } )
        Redirect(routes.Scheduler.overview)
    }    
   
    // terminate a schedule
    def terminate(name: String) = Action {
        scheduler ! new KillRequest(name)
        Redirect(routes.Scheduler.overview)
    }
    
    /**
     * Shows the form for getting configs
     */
    def showConfigs() = Action { implicit request => {
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        
        // Get the path from the body
        val path = body("path").head.split("/").filter(elem => !elem.isEmpty)
        
        // Load the files and folders from the config repository
        val configRepo = {
            val location = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
            if (location.last != '/') location + "/"
            else location
        }
        val files = new File(configRepo + path.mkString("/")).listFiles
        
        // Get configs
        val configs = files.filter(!_.isDirectory).map(cfg => cfg.getName.take(cfg.getName.size - 5))
        // Get subfolders
        val subfolders = files.filter(_.isDirectory).map(fldr => fldr.getName)
        
        // Invoke view
        Ok(views.html.scheduler.showConfigs(
                path, configs, subfolders
        ))
    }}
    
}