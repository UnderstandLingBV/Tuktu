package tuktu.api.scheduler

import java.util.{ TimeZone, UUID }
import scala.collection.mutable.Map
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.util.Timeout
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
case class KillByNameRequest(
        configName: String
)

abstract class Schedule(actor: ActorRef) {
    def description: String = ???
    def cancel: Unit = ???
}
class DelayedSchedule(actor: ActorRef, scheduler: DelayedScheduler) extends Schedule(actor) {
    val schedule = Akka.system.scheduler.scheduleOnce(
        scheduler.delay,
        actor,
        scheduler.dispatchRequest)

    val dt = org.joda.time.DateTime.now.plus(scheduler.delay.toMillis)
    override def description = "Once on " + dt.toString
    override def cancel = schedule.cancel
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
    // Even after a cron job has been cancelled, its name can not be reused; work-around by adding a UUID
    val uniqueName = name + UUID.randomUUID.toString
    quartzScheduler.createSchedule(uniqueName, None, scheduler.cronSchedule, None, TimeZone.getDefault)
    quartzScheduler.schedule(uniqueName, actor, scheduler.dispatchRequest)

    override def description = scheduler.cronSchedule
    override def cancel = quartzScheduler.cancelJob(uniqueName)
}

class TuktuScheduler(actor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // a list of active schedulers
    val schedulers = Map[String, (String, Schedule)]()

    def clearExpired: PartialFunction[Any, Any] = {
        case any =>
            for ((name, (_, schedule: DelayedSchedule)) <- schedulers if schedule.dt.isBeforeNow) {
                schedulers -= name
            }
            any
    }

    val quartzScheduler = QuartzSchedulerExtension(Akka.system)

    def receive = clearExpired andThen {
        case schedule: DelayedScheduler =>
            // If overwriting, cancel old schedule, and overwrite with new one
            schedulers.get(schedule.name).foreach { case (_, schedule) => schedule.cancel }
            schedulers += schedule.name -> (schedule.dispatchRequest.configName, new DelayedSchedule(actor, schedule))

        case schedule: SimpleScheduler =>
            // If overwriting, cancel old schedule, and overwrite with new one
            schedulers.get(schedule.name).foreach { case (_, schedule) => schedule.cancel }
            schedulers += schedule.name -> (schedule.dispatchRequest.configName, new SimpleSchedule(actor, schedule))

        case schedule: CronScheduler =>
            // If overwriting, cancel old schedule, and overwrite with new one
            schedulers.get(schedule.name).foreach { case (_, schedule) => schedule.cancel }
            schedulers += schedule.name -> (schedule.dispatchRequest.configName, new CronSchedule(actor, schedule, quartzScheduler, schedule.name))

        case _: Overview =>
            sender ! schedulers.toMap

        case kr: KillRequest =>
            schedulers.get(kr.name) foreach {
                case (_, schedule) =>
                    schedule.cancel
                    schedulers -= kr.name
            }

        case kr: KillByNameRequest =>
            // Get all the ones that are scheduled for this configName, cancel them, and remove them from schedulers
            val namesToStop = for ((name, (configName, schedule)) <- schedulers if configName == kr.configName) yield {
                schedule.cancel
                name
            }
            schedulers --= namesToStop

        case _ => {} // Needed because andThen parameter needs to be a full function and not a PartialFunction
    }
}