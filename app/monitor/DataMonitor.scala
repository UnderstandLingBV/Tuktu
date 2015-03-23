package monitor

import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global

class DataMonitor() extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var monitorData = new java.util.HashMap[String, java.util.HashMap[MPType, java.util.HashMap[String, Int]]]()
    var appMonitor = collection.mutable.Map[String, AppMonitorObject]()

    def receive() = {
        case "init" => {
            // Initialize monitor
        }
        case amp: AppMonitorPacket => amp.status match {
            case "done" => {
                appMonitor -= amp.name
            }
            case "start" => {
                if (!appMonitor.contains(amp.name)) appMonitor += amp.name -> new AppMonitorObject(amp.name, amp.timestamp)
            }
            case _ => {}
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
        case mop: MonitorOverviewPacket => sender ! appMonitor.toMap
    }
}