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


class TuktuScheduler(actor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // a list of active schedulers
    var schedulers = Map[String, Option[Cancellable]]()

    val quartzScheduler = QuartzSchedulerExtension(Akka.system)
    
    def receive() = {        
        case schedule: SimpleScheduler => {            
            schedulers += schedule.name -> Some(Akka.system.scheduler.schedule(
                    schedule.initialDelay,
                    schedule.interval,
                    actor,
                    schedule.dispatchRequest
            ))
        }
        case schedule: CronScheduler => {
            quartzScheduler.createSchedule(schedule.name, None, schedule.cronSchedule, None, Calendar.getInstance().getTimeZone())
            quartzScheduler.schedule(schedule.name, actor, schedule.dispatchRequest)
            
            schedulers += (schedule.name -> None)
        }
        case _: Overview => sender ! schedulers.keys.toList
        case kr: KillRequest => {
            schedulers(kr.name) match {
                case Some(cancellable) => cancellable.cancel
                case None => quartzScheduler.cancelJob(kr.name)
            }

            schedulers -= kr.name
        }
        case _ => {} 
    }
//    
//    def dispatcherActorRef = {
//         Await.result(Akka.system.actorSelection("user/TuktuDispatcher").resolveOne()(timeout.duration), timeout.duration)
//    }
}