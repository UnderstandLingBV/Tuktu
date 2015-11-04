package monitor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.Logger
import tuktu.api._
import tuktu.api.types.ExpirationMap
import java.util.Date
import play.api.Play

class DataMonitor extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Monitoring maps
    var appMonitor = ExpirationMap[String, AppMonitorObject](
        Play.current.configuration.getInt("tuktu.monitor.finish_expiration").getOrElse(30) * 60 * 1000)

    // Mapping of all subflows
    val subflowMap = collection.mutable.Map.empty[String, String]

    // Map of UUID -> Actor
    val uuidActorMap = collection.mutable.Map.empty[String, ActorRef]

    // Keep track of a list of actors we need to notify on push base about events happening
    val eventListeners = collection.mutable.Set.empty[ActorRef]

    def receive = {
        case any => {
            handle(any)
            for (listener <- eventListeners) listener.forward(any)
        }
    }

    def handle: PartialFunction[Any, Unit] = {
        case "init" => {
            // Initialize monitor
        }
        case fmp: SubflowMapPacket => {
            // Add to our map
            fmp.subflows.foreach(subflow => subflowMap +=
                subflow.path.toStringWithoutAddress -> fmp.mailbox.path.toStringWithoutAddress)
        }
        case aip: ActorIdentifierPacket => {
            // Add the ID and actor ref to our map
            uuidActorMap += aip.uuid -> aip.mailbox
            val mailbox_address = aip.mailbox.path.toStringWithoutAddress
            implicit val current = System.currentTimeMillis
            if (!appMonitor.contains(mailbox_address))
                appMonitor = appMonitor + (mailbox_address -> new AppMonitorObject(aip.mailbox, aip.uuid, aip.instanceCount, aip.timestamp))
        }
        case enp: ErrorNotificationPacket => {
            // Get the generator and kill it
            if (uuidActorMap.contains(enp.uuid)) {
                val generator = uuidActorMap(enp.uuid)
                generator ! new StopPacket
            }
        }
        case amel: AddMonitorEventListener    => eventListeners += sender
        case rmel: RemoveMonitorEventListener => eventListeners -= sender
        case amp: AppMonitorPacket => {
            implicit val current = amp.timestamp
            amp.status match {
                case "done" => {
                    // Get app from appMonitor  
                    appMonitor.get(amp.getParentName) match {
                        case Some(app) => {
                            app.finished_instances += 1

                            if (app.instances == app.finished_instances) {
                                // Update end time and start expiration
                                app.endTime = current
                                appMonitor.expire(amp.getParentName)
                            }
                        }
                        case None => {
                            Logger.error("DataMonitor received 'done' for unknown app: " + amp.getName)
                        }
                    }
                }
                case "kill" => {
                    // Get app from appMonitor  
                    appMonitor.get(amp.getParentName) match {
                        case Some(app) => {
                            // Update end time and start expiration
                            app.endTime = current
                            appMonitor.expire(amp.getParentName)
                        }
                        case None => {
                            Logger.error("DataMonitor received 'kill' for unknown app: " + amp.getName)
                        }
                    }
                }
                case _ => { Logger.warn("Unknown status: " + amp.status) }
            }

            // Forward packet to all listeners
            eventListeners.foreach(listener => listener ! amp)
        }
        case pmp: ProcessorMonitorPacket => {
            implicit val current = pmp.timestamp
            val mailbox_address = uuidActorMap.get(pmp.uuid) match {
                case Some(actor) => actor.path.toStringWithoutAddress
                case None        => ""
            }
            appMonitor.get(mailbox_address) match {
                case Some(app) => {
                    // Renew expiration and update maps
                    app.endTime = current
                    appMonitor.expire(mailbox_address, false)

                    val latest = app.processorDataPackets.getOrElseUpdate(pmp.processor_id, collection.mutable.Map.empty)
                    latest(pmp.typeOf) = pmp.data
                    val count = app.processorDataPacketCount.getOrElseUpdate(pmp.processor_id, collection.mutable.Map.empty.withDefaultValue(0))
                    count(pmp.typeOf) += 1
                }
                case None => {
                    Logger.error("DataMonitor received ProcessorMonitorPacket for unkown app with uuid: " + pmp.uuid)
                }
            }
        }
        case mp: MonitorPacket => {
            implicit val current = mp.timestamp
            val mailbox_address = uuidActorMap.get(mp.uuid) match {
                case Some(actor) => actor.path.toStringWithoutAddress
                case None        => ""
            }
            appMonitor.get(mailbox_address) match {
                case Some(app) => {
                    // Renew expiration and update maps
                    app.endTime = current
                    appMonitor.expire(mailbox_address, false)

                    val count = app.flowDataPacketCount.getOrElseUpdate(mp.branch, collection.mutable.Map.empty.withDefaultValue(0))
                    count(mp.typeOf) += mp.amount
                }
                case None => {
                    Logger.error("DataMonitor received MonitorPacket for unkown app with uuid: " + mp.uuid)
                }
            }
        }
        case mop: MonitorOverviewRequest => {
            implicit def current = System.currentTimeMillis
            val (running, finished) = appMonitor.partitioned
            sender ! new MonitorOverviewResult(
                running,
                finished,
                subflowMap toMap)
        }
        case m => Logger.error("Monitor received unknown message: " + m)
    }
}