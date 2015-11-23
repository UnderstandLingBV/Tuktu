package controllers

import java.util.Calendar

import scala.collection.mutable.Map
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.actorRef2Scala
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka

import tuktu.api.DispatchRequest

case class DelayedScheduler(
        name: String,
        delay: FiniteDuration,
        dispatchRequest: DispatchRequest
)
case class SimpleScheduler(
        name: String,
        initialDelay: FiniteDuration,
        interval: FiniteDuration,        
        dispatchRequest: DispatchRequest            
)
case class CronScheduler(
        name: String,
        cronSchedule: String,
        dispatchRequest: DispatchRequest
)
case class Overview()
case class KillRequest(
        name: String        
)

abstract class Schedule(actor: ActorRef) {
    def description: String = ???
    def cancel: Unit = ???
}
class SimpleSchedule(actor: ActorRef, scheduler: SimpleScheduler) extends Schedule(actor) {
    val schedule = Akka.system.scheduler.schedule(
        scheduler.initialDelay,
        scheduler.interval,
        actor,
        scheduler.dispatchRequest)

    override def description = "Every " + scheduler.interval.length + " " + scheduler.interval.unit.toString.toLowerCase
    override def cancel = schedule.cancel
}
class CronSchedule(actor: ActorRef, scheduler: CronScheduler, quartzScheduler: QuartzSchedulerExtension, name: String) extends Schedule(actor) {
    quartzScheduler.createSchedule(name, None, scheduler.cronSchedule, None, Calendar.getInstance.getTimeZone)
    quartzScheduler.schedule(name, actor, scheduler.dispatchRequest)

    override def description = scheduler.cronSchedule
    override def cancel = quartzScheduler.cancelJob(name)
}

class TuktuScheduler(actor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // a list of active schedulers
    var schedulers = Map[String, Schedule]()

    val quartzScheduler = QuartzSchedulerExtension(Akka.system)

    def receive() = {
        case schedule: DelayedScheduler => {
            Akka.system.scheduler.scheduleOnce(schedule.delay, actor, schedule.dispatchRequest)
        }
        case schedule: SimpleScheduler => {
            // If overwriting, cancel old schedule
            if (schedulers.contains(schedule.name)) schedulers(schedule.name).cancel
            // Add new schedule
            schedulers += schedule.name -> new SimpleSchedule(actor, schedule)
        }
        case schedule: CronScheduler => {
            val uniqueName = schedule.name + "_" + java.util.UUID.randomUUID.toString
            schedulers += uniqueName -> new CronSchedule(actor, schedule, quartzScheduler, uniqueName)
        }
        case _: Overview => sender ! schedulers.mapValues(_.description).toList.sorted
        case kr: KillRequest => {
            schedulers(kr.name).cancel
            schedulers -= kr.name
        }
        case _ => {}
    }
}