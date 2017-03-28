package controllers

import java.nio.file.{ Files, Paths }

import scala.concurrent.duration.{ Duration, FiniteDuration }

import play.api.cache.Cache
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.{ Json, JsObject }

import tuktu.api.DispatchRequest
import tuktu.api.scheduler._

/**
 * Load flows from an autostart file and either immediately start running them or
 * schedule them based on the cron settings.
 */
object AutoStart {
    // name of the config file
    val path = Paths.get(Play.current.configuration.getString("tuktu.autostart").getOrElse("conf/autostart.json"))

    if (Files.isRegularFile(path)) {
        val cfg = Json.parse(Files.readAllBytes(path))

        // Cache
        Cache.set("tuktu.scheduler.autostart", (cfg \ "autostart").as[List[JsObject]])

        // kick off each job
        for (job <- (cfg \ "autostart").as[List[JsObject]]) {
            val id = (job \ "id").as[String]
            val dispatchRequest = new DispatchRequest(id, None, false, false, false, None)
            val cron = (job \ "cron").asOpt[String]
            val delay = (job \ "delay").asOpt[String]

            delay match {
                case Some(d) =>
                    // A delayed start is defined
                    Akka.system.actorSelection("user/TuktuScheduler") ! new DelayedScheduler(id, Duration(d).asInstanceOf[FiniteDuration], dispatchRequest)
                case None =>
                    cron match {
                        // A cron schedule is defined, start cron job
                        case Some(c) => Akka.system.actorSelection("user/TuktuScheduler") ! new CronScheduler(id, c, dispatchRequest)
                        // Neither delayed nor cron schedule is defined, start immediately
                        case None    => Akka.system.actorSelection("user/TuktuDispatcher") ! dispatchRequest
                    }
            }
        }
    }
}