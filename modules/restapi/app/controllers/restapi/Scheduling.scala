package controllers.restapi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.{ Json, JsArray, JsObject }
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api.DispatchRequest
import tuktu.api.scheduler.CronScheduler
import tuktu.api.scheduler.KillRequest
import tuktu.api.scheduler.Overview
import tuktu.api.scheduler.SimpleScheduler

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets.UTF_8

import scala.collection.JavaConversions._

object Scheduling extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Retrieve the TuktuScheduler
    val scheduler = Akka.system.actorSelection("user/TuktuScheduler")

    /**
     * Overview of scheduled jobs
     */
    def overview() = Action.async { implicit request =>
        // Get the monitor
        val fut = (scheduler ? new Overview()).asInstanceOf[Future[Map[String, (String, tuktu.api.scheduler.Schedule)]]]
        fut.map(res =>
            Ok(JsArray(
                    res.toList.sortBy(_._1).map {sch =>
                        Json.obj(
                                "name" -> sch._1,
                                "config" -> sch._2._1,
                                "schedule" -> sch._2._2.description
                        )
                    }
            ))
        )
    }

    /**
     * Terminate a scheduled job by name
     */
    def terminate() = Action.async { implicit request =>
        Future {
            val name = (request.body.asJson.getOrElse(Json.obj()).as[JsObject] \ "name").as[String]
            scheduler ! new KillRequest(name)
            Ok(Json.obj())
        }
    }

    /**
     * Schedule (multiple) simple jobs
     */
    def simple()  = Action.async { implicit request =>
        Future {
            val json = request.body.asJson.getOrElse(Json.arr()).as[List[JsObject]]

            json.foreach(job => {
                // Get parameters we need
                val initialDelay = Duration((job \ "initial_delay").as[String]).asInstanceOf[FiniteDuration]
                val interval = Duration((job \ "interval").as[String]).asInstanceOf[FiniteDuration]
                val dispatchRequest = new DispatchRequest((job \ "config").as[String], None, false, false, false, None)     

                scheduler ! new SimpleScheduler((job \ "name").as[String], initialDelay, interval, dispatchRequest)
            })

            Ok(Json.obj())
        }
    }

    /**
     * Schedules (multiple) cron jobs
     */
    def cron()  = Action.async { implicit request =>
        Future { 
            val json = request.body.asJson.map(_.asOpt[List[JsObject]]).flatten.getOrElse(Nil)

            for (job <- json) {
                // Start cron job
                val dispatchRequest = new DispatchRequest((job \ "config").as[String], None, false, false, false, None)     
                scheduler ! new CronScheduler((job \ "name").as[String], (job \ "schedule").as[String], dispatchRequest)

                // Should we add this to the auto start or not?
                if ((job \ "persist").asOpt[Boolean].getOrElse(false)) {
                    // Get the autostart cache
                    val autostart = Cache.getAs[List[JsObject]]("tuktu.scheduler.autostart").getOrElse(List())
                    val path = Paths.get(Play.current.configuration.getString("tuktu.autostart").getOrElse("conf/autostart.json"))

                    // Define JSON
                    val json = Json.obj(
                            "id" -> (job \ "config").as[String],
                            "cron" -> (job \ "schedule").as[String]
                    )
                    // Write out
                    val content = Json.prettyPrint(Json.obj("autostart" -> new JsArray(autostart ++ List(json))))

                    Files.write(path, content.getBytes(UTF_8))

                    // Add to cache
                    Cache.set("tuktu.scheduler.autostart", autostart ++ List(json))
                }
            }
            Ok
        }
    }
}