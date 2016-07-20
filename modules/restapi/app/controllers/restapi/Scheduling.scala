package controllers.restapi

import play.api.cache.Cache
import akka.util.Timeout
import play.api.mvc.Controller
import concurrent.duration.DurationInt
import play.api.Play.current
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import tuktu.api.scheduler.Overview
import play.api.mvc.Action
import scala.concurrent.Future
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsObject
import tuktu.api.scheduler.KillRequest
import tuktu.api.DispatchRequest
import scala.concurrent.duration.FiniteDuration
import tuktu.api.scheduler.SimpleScheduler
import scala.concurrent.duration.Duration
import tuktu.api.scheduler.CronScheduler

object Scheduling extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Retrieve the TuktuScheduler
    val scheduler = Akka.system.actorSelection("user/TuktuScheduler")
    
    /**
     * Overview of scheduled jobs
     */
    def overview() = Action.async { implicit request =>
        // Get the monitor
        val fut = (scheduler ? new Overview()).asInstanceOf[Future[List[(String, String)]]]
        fut.map(res =>
            Ok(Json.arr(
                    res.map(job => {
                        val (name, schedule) = (job._1, job._2)
                        Json.obj(
                                "name" -> name,
                                "schedule" -> schedule
                        )
                    }).toList
            ))
        )
    }
    
    /**
     * Terminate a scheduled job by name
     */
    def terminate() = Action.async { implicit request =>
        val name = (request.body.asJson.getOrElse(Json.obj()).as[JsObject] \ "name").as[String]
        scheduler ! new KillRequest(name)
        Future { Ok(Json.obj()) }
    }
    
    /**
     * Schedule (multiple) simple jobs
     */
    def simple()  = Action.async { implicit request =>
        val json = request.body.asJson.getOrElse(Json.arr()).as[List[JsObject]]
        
        json.foreach(job => {
            // Get parameters we need
            val initialDelay = Duration((job \ "initial_delay").as[String]).asInstanceOf[FiniteDuration]
            val interval = Duration((job \ "interval").as[String]).asInstanceOf[FiniteDuration]
            val dispatchRequest = new DispatchRequest((job \ "config").as[String], None, false, false, false, None)     
        
            scheduler ! new SimpleScheduler((job \ "name").as[String], initialDelay, interval, dispatchRequest)
        })
        
        Future { Ok(Json.obj()) }
    }
    
    /**
     * Schedules (multiple) cron jobs
     */
    def cron()  = Action.async { implicit request =>
        val json = request.body.asJson.getOrElse(Json.arr()).as[List[JsObject]]
        
        json.foreach(job => {
            // Start cron job
            val dispatchRequest = new DispatchRequest((job \ "config").as[String], None, false, false, false, None)     
            scheduler ! new CronScheduler((job \ "name").as[String], (job \ "schedule").as[String], dispatchRequest)
        })
        Future { Ok(Json.obj()) }
    }
}