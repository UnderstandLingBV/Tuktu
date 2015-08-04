package controllers

import java.nio.file.Files
import java.nio.file.Paths

import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._

import tuktu.api.DispatchRequest

/**
 * Load flows from an autostart file and either immediately start running them or
 * schedule them based on the cron settings.
 * 
 */
object AutoStart {
    // name of the config file
    val filename = Play.current.configuration.getString("tuktu.autostart").getOrElse("conf/autostart.json")

    init

    def init = {
        if (Files.exists(Paths.get(filename))) {
            val configFile = scala.io.Source.fromFile(filename, "utf-8")
            val cfg = Json.parse(configFile.mkString).as[JsObject]
            configFile.close
            
            // kick off each job
            (cfg \ "autostart").asInstanceOf[JsArray].value.foreach { job =>
                {
                    val id = (job \ "id").asInstanceOf[JsString].value
                    val dispatchRequest = new DispatchRequest(id, None, false, false, false, None)
                    val cron = job \ "cron"
                    // when no cron is defined, immediately start running this job
                    if (cron.isInstanceOf[JsUndefined]) {
                        Akka.system.actorSelection("user/TuktuDispatcher") ! dispatchRequest
                    } else {
                        // a cron schedule is defined, start a cron job
                        val cronSchedule = cron.asInstanceOf[JsString].value
                        Akka.system.actorSelection("user/TuktuScheduler") ! new CronScheduler(id, cronSchedule, dispatchRequest)
                    }
                }
            }
        }
    }
}