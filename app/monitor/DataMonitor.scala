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
import tuktu.api.utils.MapToJsObject
import java.util.Date
import play.api.Play
import play.api.libs.json.Json

class DataMonitor extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Monitoring maps, uuid -> AppMonitorObject
    var appMonitor = Map.empty[String, AppMonitorObject]

    // Keep track of ActorRef addresses -> uuid
    val uuidMap = collection.mutable.Map.empty[String, String]

    // Mapping of all subflows
    val subflowMap = collection.mutable.Map.empty[String, String]

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
        case "clearFinished" => {
            appMonitor = appMonitor.filter(_._2.endTime == None)
        }
        case fmp: SubflowMapPacket => {
            // Add to our map
            fmp.subflows.foreach(subflow => subflowMap +=
                subflow.path.toStringWithoutAddress -> fmp.mailbox.path.toStringWithoutAddress)
        }
        case aip: AppInitPacket => {
            if (!appMonitor.contains(aip.uuid))
                appMonitor = appMonitor.filterNot(_._2.is_expired(System.currentTimeMillis)) + (aip.uuid -> new AppMonitorObject(aip.uuid, aip.instanceCount, aip.timestamp))
            aip.mailbox.collect {
                case mailbox => {
                    appMonitor(aip.uuid).actors += mailbox
                    uuidMap += mailbox.path.toStringWithoutAddress -> aip.uuid
                }
            }
        }
        case asp: AppStopPacket => {
            appMonitor.get(asp.uuid) match {
                case Some(app) => {
                    app.finished_instances += 1

                    if (app.instances == app.finished_instances)
                        // Update end time and start expiration
                        app.expire(asp.timestamp)
                }
                case None => {
                    Logger.warn("DataMonitor received AppStopPacket for unknown app with uuid: " + asp.uuid)
                }
            }
        }
        case enp: ErrorNotificationPacket => {
            // Get the actors and stop them
            appMonitor.get(enp.uuid) collect {
                case app => {
                    app.errors += enp.processorName -> ("Error happened at flow: " + enp.configName + ", processor: " + enp.processorName + ", id: " + enp.uuid + ", on Input:\n" + enp.input + "\n" + enp.error.toString)
                    app.actors.foreach(_ ! new StopPacket)
                }
            }
        }
        case amel: AddMonitorEventListener    => eventListeners += sender
        case rmel: RemoveMonitorEventListener => eventListeners -= sender
        case amp: AppMonitorPacket => {
            implicit val current = amp.timestamp
            val uuid = uuidMap.get(amp.getParentName) match {
                case Some(id) => id
                case None => {
                    Logger.warn("DataMonitor received AppMonitorPacket for unknown app: " + amp.getName)
                    ""
                }
            }
            amp.status match {
                case "done" => {
                    // Get app from appMonitor  
                    appMonitor.get(uuid) match {
                        case Some(app) => {
                            app.finished_instances += 1

                            if (app.instances == app.finished_instances)
                                // Update end time and start expiration
                                app.expire(current)
                        }
                        case None => {
                            Logger.warn("DataMonitor received 'done' for unknown app: " + amp.getName)
                        }
                    }
                }
                case "kill" => {
                    // Get app from appMonitor  
                    appMonitor.get(uuid) match {
                        case Some(app) => {
                            // Update end time and start expiration
                            app.expire(current)
                        }
                        case None => {
                            Logger.warn("DataMonitor received 'kill' for unknown app: " + amp.getName)
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
            appMonitor.get(pmp.uuid) match {
                case Some(app) => {
                    // Renew expiration and update maps
                    app.expire(current, false)

                    val latest = app.processorDataPackets.getOrElseUpdate(pmp.processor_id, collection.mutable.Map.empty)
                    latest(pmp.typeOf) = pmp.data

                    val count = app.processorDatumCount.getOrElseUpdate(pmp.processor_id, collection.mutable.Map.empty.withDefaultValue(0))
                    count(pmp.typeOf) += pmp.data.data.size

                    val DPcount = app.processorDataPacketCount.getOrElseUpdate(pmp.processor_id, collection.mutable.Map.empty.withDefaultValue(0))
                    DPcount(pmp.typeOf) += 1

                    // If BeginType, queue up new BeginTime
                    if (pmp.typeOf == BeginType) {
                        val beginTimes = app.processorBeginTimes.getOrElseUpdate(pmp.processor_id, collection.mutable.Queue.empty)
                        beginTimes.enqueue(pmp.timestamp)
                    } else if (pmp.typeOf == EndType) {
                        val beginTimes = app.processorBeginTimes.getOrElseUpdate(pmp.processor_id, collection.mutable.Queue.empty)
                        if (beginTimes.nonEmpty) {
                            val durations = app.processorDurations.getOrElseUpdate(pmp.processor_id, collection.mutable.ListBuffer.empty)
                            durations += pmp.timestamp - beginTimes.dequeue
                        } else {
                            Logger.warn("DataMonitor received more ProcessorMonitorPackets of type EndType than BeginType for app " + pmp.uuid + " and processor " + pmp.processor_id)
                        }
                    }
                }
                case None => {
                    Logger.warn("DataMonitor received ProcessorMonitorPacket for unkown app with uuid: " + pmp.uuid)
                }
            }
        }
        case mp: MonitorPacket => {
            implicit val current = mp.timestamp
            appMonitor.get(mp.uuid) match {
                case Some(app) => {
                    // Renew expiration and update maps
                    app.expire(current, false)

                    val count = app.flowDatumCount.getOrElseUpdate(mp.branch, collection.mutable.Map.empty.withDefaultValue(0))
                    count(mp.typeOf) += mp.amount

                    val DPcount = app.flowDataPacketCount.getOrElseUpdate(mp.branch, collection.mutable.Map.empty.withDefaultValue(0))
                    DPcount(mp.typeOf) += 1
                }
                case None => {
                    Logger.warn("DataMonitor received MonitorPacket for unkown app with uuid: " + mp.uuid)
                }
            }
        }
        case mop: MonitorOverviewRequest => {
            appMonitor = appMonitor.filterNot(_._2.is_expired(System.currentTimeMillis))
            val partitions = appMonitor.groupBy(_._2.endTime == None)
            sender ! new MonitorOverviewResult(
                partitions.getOrElse(true, Map.empty),
                partitions.getOrElse(false, Map.empty),
                subflowMap toMap)
        }
        case mldp: MonitorLastDataPacketRequest => {
            implicit val current = System.currentTimeMillis
            val result = appMonitor.get(mldp.flow_name) match {
                case None => ("Nothing recorded for this flow yet.", "Nothing recorded for this flow yet.")
                case Some(appData) => {
                    val in = appData.processorDataPackets.get(mldp.processor_id) match {
                        case None => "No DataPackets recorded for this procsesor yet."
                        case Some(map) => map.get(BeginType) match {
                            case None     => "No incoming DataPackets recorded for this processor yet."
                            case Some(dp) => Json.prettyPrint(Json.toJson(dp.data.map(datum => MapToJsObject(datum))))
                        }
                    }
                    val out = appData.processorDataPackets.get(mldp.processor_id) match {
                        case None => "No DataPackets recorded for this procsesor yet."
                        case Some(map) => map.get(EndType) match {
                            case None     => "No processed DataPackets recorded for this processor yet."
                            case Some(dp) => Json.prettyPrint(Json.toJson(dp.data.map(datum => MapToJsObject(datum))))
                        }
                    }
                    (in, out)
                }
            }
            sender ! result
        }
        case m => Logger.warn("DataMonitor received unknown message: " + m)
    }
}