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

class DataMonitor extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Monitoring maps
    val monitorData = collection.mutable.Map.empty[String, collection.mutable.Map[MPType, collection.mutable.Map[String, Int]]]
    val appMonitor = collection.mutable.Map.empty[String, AppMonitorObject]
    
    // Map of UUID -> Actor
    val uuidActorMap = collection.mutable.Map.empty[String, ActorRef]
    
    // Keep track of a list of actors we need to notify on push base about events happening
    val eventListeners = collection.mutable.HashSet.empty[ActorRef]
    
    // Keep track of finished jobs that can expire
    var finishedJobs = ExpirationMap[String, (Long, Long, Int, Int)](
            Play.current.configuration.getInt("tuktu.monitor.finish_expiration").getOrElse(30) * 60 * 1000
    )

    def receive() = {
        case "init" => {
            // Initialize monitor
        }
        case aip: ActorIdentifierPacket => {
            // Add the ID and actor ref to our map
            uuidActorMap += aip.uuid -> aip.mailbox
            val mailbox_address = aip.mailbox.path.toStringWithoutAddress
            if (!appMonitor.contains(mailbox_address))
                appMonitor += mailbox_address -> new AppMonitorObject(aip.mailbox, aip.instanceCount)
        }
        case enp: ErorNotificationPacket => {
            // Get the generator and kill it
            if (uuidActorMap.contains(enp.uuid)) {
                val generator = uuidActorMap(enp.uuid)
                generator ! new StopPacket
            }
        }
        case amel: AddMonitorEventListener => eventListeners += sender
        case rmel: RemoveMonitorEventListener => eventListeners -= sender
        case amp: AppMonitorPacket => {
            amp.status match {
                case "done" => {
                    // Get app from appMonitor  
                    appMonitor.get(amp.getParentName) match {
                        case Some(app) => {
                            app.finished_instances += 1
                            
                            if (app.instances == app.finished_instances) {
                                // Remove from current apps, add to finished jobs                    
                                appMonitor -= amp.getParentName
                                
                                implicit def currentTime = System.currentTimeMillis
                                finishedJobs = finishedJobs + (amp.getParentName, (app.startTime, currentTime, app.finished_instances, app.instances))
                            }
                        }
                        case None => {
                            System.err.println("DataMonitor received 'done' for unknown app: " + amp.getName)
                        }
                    }
                }
                case "kill" => {
                    // get app from appMonitor  
                    appMonitor.get(amp.getParentName) match {
                        case Some(app) => {
                            // Remove from current apps, add to finished jobs                    
                            appMonitor -= amp.getParentName

                            implicit def currentTime = System.currentTimeMillis
                            finishedJobs = finishedJobs + (amp.getParentName, (app.startTime, currentTime, app.finished_instances, app.instances))
                        }
                        case None => {
                            System.err.println("DataMonitor received 'kill' for unknown app: " + amp.getName)
                        }
                    }
                }
                case _ => { println("Unknown status: " + amp.status) }
            }
            
            // Forward packet to all listeners
            eventListeners.foreach(listener => listener ! amp)
        }
        case mp: MonitorPacket => {
            // Initialize if we have to
            val internalMap = monitorData.getOrElseUpdate(mp.uuid, collection.mutable.Map[MPType, collection.mutable.Map[String, Int]](
                    BeginType -> collection.mutable.Map[String, Int]().withDefaultValue(0),
                    EndType -> collection.mutable.Map[String, Int]().withDefaultValue(0),
                    CompleteType -> collection.mutable.Map[String, Int]().withDefaultValue(0)))

            // Increment
            internalMap(mp.typeOf)(mp.branch) += mp.amount
        }
        case mop: MonitorOverviewPacket => {
            implicit def currentTime = System.currentTimeMillis
            sender ! new MonitorOverviewResult(
                appMonitor.toMap,
                finishedJobs.toMap,
                monitorData.map(x => uuidActorMap.get(x._1).map(_.path.toStringWithoutAddress).getOrElse(x._1) -> x._2).toMap
            )
        }
        case m => println("Monitor received unknown message: " + m)
    }
}