package monitor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import tuktu.api._
import tuktu.api.types.ExpirationMap
import java.util.Date
import play.api.Play

class DataMonitor() extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    val monitorData = new java.util.HashMap[String, java.util.HashMap[MPType, java.util.HashMap[String, Int]]]()
    val appMonitor = collection.mutable.Map[String, AppMonitorObject]()
    
    // Keep track of a list of actors we need to notify on push base about events happening
    val eventListeners = collection.mutable.HashSet.empty[ActorRef]
    
    // Keep track of finished jobs that can expire
    var finishedJobs = ExpirationMap[String, (Long, Long)](
            Play.current.configuration.getInt("tuktu.monitor.finish_expiration").getOrElse(30) * 60 * 1000
    )

    def receive() = {
        case "init" => {
            // Initialize monitor
        }
        case amel: AddMonitorEventListener => eventListeners += sender
        case rmel: RemoveMonitorEventListener => eventListeners -= sender
        case amp: AppMonitorPacket => {
            amp.status match {
                case "done" => {
                    // Remove from current apps, add to finished jobs
                    appMonitor -= amp.name
                    
                    implicit def currentTime = new Date().getTime
                    finishedJobs = finishedJobs + (amp.name + "_at_" + currentTime.toString, (amp.timestamp, currentTime / 1000L))
                }
                case "start" => {
                    if (!appMonitor.contains(amp.name)) appMonitor += amp.name -> new AppMonitorObject(amp.name, amp.timestamp)
                }
                case _ => {println("Unknown status")}
            }
            
            // Forward packet to all listeners
            eventListeners.foreach(listener => listener ! amp)
        }
        case mp: MonitorPacket => {
            // Initialize if we have to
            if (!monitorData.containsKey(mp.actorName)) {
                val internalMap = new java.util.HashMap[MPType, java.util.HashMap[String, Int]]()
                internalMap.put(BeginType, new java.util.HashMap[String, Int]())
                internalMap.put(EndType, new java.util.HashMap[String, Int]())
                internalMap.put(CompleteType, new java.util.HashMap[String, Int]())
                monitorData.put(mp.actorName, internalMap)
            }
            if (!monitorData.get(mp.actorName).get(mp.typeOf).containsKey(mp.branch)) {
                monitorData.get(mp.actorName).get(mp.typeOf).put(mp.branch, 0)
            }
            
            // Increment
            val x = monitorData.get(mp.actorName).get(mp.typeOf).get(mp.branch)
            monitorData.get(mp.actorName).get(mp.typeOf).put(mp.branch,
                monitorData.get(mp.actorName).get(mp.typeOf).get(mp.branch) + mp.amount)
        }
        case mop: MonitorOverviewPacket => {
            implicit def currentTime = new Date().getTime
            sender ! new MonitorOverviewResult(
                appMonitor.toMap, finishedJobs.toMap
            )
        }
        case m => println("Monitor received unknown message: " + m)
    }
}