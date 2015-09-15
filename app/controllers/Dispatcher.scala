package controllers

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor._
import akka.pattern.ask
import akka.routing.SmallestMailboxPool
import akka.util.Timeout
import controllers.nodehandler.nodeHandler
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api._
import tuktu.generators.AsyncStreamGenerator
import tuktu.generators.EOFSyncStreamGenerator
import tuktu.generators.SyncStreamGenerator
import tuktu.processors.EOFBufferProcessor
import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor

case class treeNode(
        name: String,
        parents: List[Class[_]],
        children: List[Class[_]]
)

object Dispatcher {
    /**
     * Monitoring enumeratee
     */
    def monitorEnumeratee(generatorName: String, branch: String, mpType: MPType): Enumeratee[DataPacket, DataPacket] = {
        implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        
        // Get the monitoring actor
        /*val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
        val monitorActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef*/
        
        Enumeratee.mapM(data => Future {
            if (data.data.size > 0)
                Akka.system.actorSelection("user/TuktuMonitor") ! new MonitorPacket(mpType, generatorName, branch, data.data.size)
            data
        })
    }
    
    /**
     * Takes a list of Enumeratees and iteratively composes them to form a single Enumeratee
     * @param nextId The names of the processors next to compose
     * @param processorMap A map cntaining the names of the processors and the processors themselves
     */
    def buildEnums (
            nextIds: List[String],
            processorMap: Map[String, ProcessorDefinition],
            monitorName: String,
            genActor: Option[ActorRef]
    ): (String, List[Enumeratee[DataPacket, DataPacket]]) = {
        // Get log level
        val logLevel = Cache.getAs[String]("logLevel").getOrElse("none")
        // Generate the logEnumeratee ID
        val idString = java.util.UUID.randomUUID.toString
        
        /**
         * Builds a chain of processors recursively
         */
        def buildSequential(
                procName: String,
                accum: Enumeratee[DataPacket, DataPacket],
                iterationCount: Int
        ): Enumeratee[DataPacket, DataPacket] = {
            // Get processor definition
            val pd = processorMap(procName)
            
            // Initialize the processor
            val procClazz = Class.forName(pd.name)
            // Check if this processor is a bufferer
            if (classOf[BufferProcessor].isAssignableFrom(procClazz) || classOf[BaseConcurrentProcessor].isAssignableFrom(procClazz)) {
                /*
                 * Bufferer processor, we pass on an actor that can take up the datapackets with the regular
                 * flow and cut off regular flow for now
                 */
                
                // Get sync or not
                val sync = (pd.config \ "sync").asOpt[Boolean].getOrElse(false)
                // Create generator
                val generator = sync match {
                    case true => {
                        if (classOf[EOFBufferProcessor].isAssignableFrom(procClazz)) {
                            Akka.system.actorOf(Props(classOf[tuktu.generators.EOFSyncStreamGenerator], "",
                                pd.next.map(processorName => {
                                    // Create the new lines of processors
                                    buildSequential(processorName, utils.logEnumeratee(idString), iterationCount + 1)
                                }),
                                genActor
                            ))
                        } else {
                            Akka.system.actorOf(Props(classOf[tuktu.generators.SyncStreamGenerator], "",
                                pd.next.map(processorName => {
                                    // Create the new lines of processors
                                    buildSequential(processorName, utils.logEnumeratee(idString), iterationCount + 1)
                                }),
                                {
                                    if (classOf[BufferProcessor].isAssignableFrom(procClazz)) genActor
                                    else None
                                }
                            ))
                        }
                    }
                    case false => {
                        Akka.system.actorOf(Props(classOf[tuktu.generators.AsyncStreamGenerator], "",
                            pd.next.map(processorName => {
                                // Create the new lines of processors
                                buildSequential(processorName, utils.logEnumeratee(idString), iterationCount + 1)
                            }),
                            None
                        ))
                    }
                }
                
                // Instantiate the processor now
                val iClazz = procClazz.getConstructor(
                        classOf[ActorRef],
                        classOf[String]
                ).newInstance(
                        generator,
                        pd.resultName
                )
            
                // Initialize the processor first
                val initMethod = procClazz.getMethods.filter(m => m.getName == "initialize").head
                initMethod.invoke(iClazz, pd.config)

                // Add method to all our entries so far
                val method = procClazz.getMethods.filter(m => m.getName == "processor").head
                // Log enumeratee or not?
                if (logLevel == "all")
                    accum compose method.invoke(iClazz).asInstanceOf[Enumeratee[DataPacket, DataPacket]] compose utils.logEnumeratee(idString)
                else
                    accum compose method.invoke(iClazz).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
            }
            else {
                // 'Regular' processor
                val iClazz = procClazz.getConstructor(classOf[String]).newInstance(pd.resultName)
            
                // Initialize the processor first
                val initMethod = procClazz.getMethods.filter(m => m.getName == "initialize").head
                initMethod.invoke(iClazz, pd.config)
                
                val method = procClazz.getMethods.filter(m => m.getName == "processor").head
                val procEnum = method.invoke(iClazz).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
                
                // Recurse, determine whether we need to branch or not
                pd.next match {
                    case List() => {
                        // No processors left, return accum
                        accum compose procEnum compose utils.logEnumeratee(idString)
                    }
                    case n::List() => {
                        // No branching, just recurse
                        buildSequential(n, accum compose procEnum compose utils.logEnumeratee(idString), iterationCount + 1)
                    }
                    case _ => {
                        // We need to branch, use the broadcasting enumeratee
                        accum compose procEnum compose buildBranch(
                                pd.next.map(nextProcessorName => {
                                    buildSequential(nextProcessorName, utils.logEnumeratee(idString), iterationCount + 1)
                                })
                        )
                    }
                }
            }
        }
        
        /**
         * Whenever we branch in a flow, we need a special enumeratee that helps us with the broadcasting. The dispatcher
         * should make sure the flow ends there and the broadcaster picks up.
         */
        def buildBranch(
                nextProcessors: List[Enumeratee[DataPacket, DataPacket]]
        ): Enumeratee[DataPacket, DataPacket] = {
            // Set up the broadcast
            val (enumerator, channel) = Concurrent.broadcast[DataPacket]
            
            // Set up broadcast
            nextProcessors.foreach(processor => {
                enumerator |>> processor &>> Iteratee.ignore
            })
            
            // Make a broadcasting Enumeratee and sink Iteratee
            Enumeratee.mapM[DataPacket]((data: DataPacket) => Future {
                // Broadcast data
                channel.push(data)
                data
            }) compose Enumeratee.onEOF(() => channel.eofAndEnd) compose utils.logEnumeratee(idString)
        }
        
        // Determine logging strategy and build the enumeratee
        Cache.getAs[String]("logLevel").getOrElse("all") match {
            case "all" => {
                // First build all Enumeratees
                (idString, nextIds.zipWithIndex.map(elem => {
                    val nextId = elem._1
                    val index = elem._2
                    
                    // Prepend a start packet for the monitor and append a stop packet
                    monitorEnumeratee(monitorName, index.toString, BeginType) compose
                    buildSequential(nextId, utils.logEnumeratee[DataPacket](idString), 0) compose
                    monitorEnumeratee(monitorName, index.toString, EndType)
                }))
            }
            case "none" => (idString, nextIds.map(nextId => buildSequential(nextId, utils.logEnumeratee[DataPacket](idString), 0)))
        }
    }
}

/**
 * This actor is the heart of Tuktu and bootstraps all flows executed by Tuktu.
 * For detailed explanation on this actor, read the manual at https://github.com/ErikTromp/Tuktu
 */
class Dispatcher(monitorActor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Get location where config files are and store in cache
    Cache.set("configRepo", Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs"))
    // Set location of this node
    Cache.set("homeAddress", Play.current.configuration.getString("akka.remote.netty.tcp.hostname").getOrElse("127.0.0.1"))
    // Set log level
    Cache.set("logLevel", Play.current.configuration.getString("tuktu.monitor.level").getOrElse("all"))
    // Get the cluster setup, which nodes are present
    Cache.set("clusterNodes", {
        val clusterNodes = scala.collection.mutable.Map[String, ClusterNode]()
        Play.current.configuration.getConfigList("tuktu.cluster.nodes").foreach(nodeList =>
            nodeList.asScala.foreach(node => {
                val host = node.getString("host").getOrElse("127.0.0.1")
                val akkaPort = node.getString("port").getOrElse("2552").toInt
                val UIPort = node.getString("uiport").getOrElse("9000").toInt
                clusterNodes += host -> new ClusterNode(host, akkaPort, UIPort)
            })
        )
        clusterNodes
    })
    
    /**
     * Builds the processor map
     */
    def buildProcessorMap(processors: List[JsObject]) = {
        // Get all processors
        (for (processor <- processors) yield {
            // Get all fields
            val processorId = (processor \ "id").as[String]
            val processorName = (processor \ "name").as[String]
            val processorConfig = (processor \ "config").as[JsObject]
            val resultName = (processor \ "result").as[String]
            val next = (processor \ "next").as[List[String]]
            
            // Create processor definition
            val procDef = new ProcessorDefinition(
                    processorId,
                    processorName,
                    processorConfig,
                    resultName,
                    next
            )
            
            // Return map
            processorId -> procDef
        }).toMap
    }
    
    /**
     * Receive function that does all the magic
     */
    def receive() = {
        case hc: HealthCheck => {
            // We are requested a health check, send back the reply
            sender ! new HealthReply()
        }
        case dr: DispatchRequest => {
            // Get config
            val config = dr.config match {
                case Some(cfg) => cfg
                case None => {
                    val configFile = scala.io.Source.fromFile(Cache.getAs[String]("configRepo").getOrElse("configs") +
                            "/" + dr.configName + ".json", "utf-8")
                    val cfg = Json.parse(configFile.mkString).as[JsObject]
                    configFile.close
                    cfg
                }
            }
            
            // Get source actor if not set yet
            val sourceActor = dr.sync match {
                case false => None
                case true => dr.sourceActor match {
                    case None => Some(sender)
                    case Some(sa) => Some(sa)
                }
            }
            
            // Start local actor, get all data processors
            val processorMap = buildProcessorMap((config \ "processors").as[List[JsObject]])

            // Get the data generators
            val generators = (config \ "generators").as[List[JsObject]]
            for ((generator, index) <- generators.zipWithIndex) {
                // Get all fields
                val generatorName = (generator \ "name").as[String]
                val generatorConfig = (generator \ "config").as[JsObject]
                val resultName = (generator \ "result").as[String]
                val next = (generator \ "next").as[List[String]]
                val nodeAddress = (generator \ "nodes").asOpt[List[JsObject]]
                
                // Parse the nodes field to see where this generator should be constructed
                val nodeList = nodeHandler.handleNodes(nodeAddress.getOrElse(List()))
                
                // Go over all nodes and submit the generator there
                for (nodeInstance <- nodeList) {
                    val hostname = nodeInstance._1
                    val instanceCount = nodeInstance._2
                    
                    // See if this one needs to be started remotely or not
                    val startRemotely = {
                        // We may or may not need to start remotely
                        if (hostname == Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")) false
                        else true
                    }
                            
                    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                    if (startRemotely && !dr.isRemote && hostname != "" && clusterNodes.contains(hostname)) {
                        // We need to start an actor on a remote location
                        val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname).akkaPort + "/user/TuktuDispatcher"
                        
                        // Get the identity
                        val fut = Akka.system.actorSelection(location) ? Identify(None)
                        val remoteDispatcher = Await.result(fut.mapTo[ActorIdentity], 5 seconds).getRef
                        
                        // Send a remoted dispatch request, which is just the obtained config
                        dr.returnRef match {
                            case true => {
                                // We need to get the actor reference and return it
                                val refFut = remoteDispatcher ? new DispatchRequest(dr.configName, Some(config), true, dr.returnRef, dr.sync, sourceActor)
                                sender ? Await.result(refFut.mapTo[ActorRef], 5 seconds)
                            }
                            case false => remoteDispatcher ! new DispatchRequest(dr.configName, Some(config), true, dr.returnRef, dr.sync, sourceActor)
                        }
                    } else {
                        if (!startRemotely) {
                            // Build the processor pipeline for this generator
                            val (idString, processorEnumeratee) = Dispatcher.buildEnums(next, processorMap, dr.configName + "/" + generatorName, dr.sourceActor)
                            
                            // Set up the generator
                            val clazz = Class.forName(generatorName)
                            
                            try {
                                // Make the amount of actors we require
                                val actorRef = {
                                    // See if this is the JS generator or not
                                    if (classOf[TuktuBaseJSGenerator].isAssignableFrom(clazz)) {
                                        // Define name based on config location
                                        val refererName = {
                                            val split = dr.configName.split("/").takeRight(2)
                                            if (split(1) == "Tuktu") split(0)
                                            else split.mkString(".")
                                        }
                                        
                                        Akka.system.actorOf(
                                            SmallestMailboxPool(instanceCount).props(
                                                Props(clazz, refererName, resultName, processorEnumeratee, dr.sourceActor)
                                            ),
                                            name = dr.configName.replaceAll("/", "_") +  "_" + clazz.getName +  "_" + index
                                        )
                                    }
                                    else
                                        Akka.system.actorOf(
                                            SmallestMailboxPool(instanceCount).props(
                                                Props(clazz, resultName, processorEnumeratee, dr.sourceActor)
                                            ),
                                            name = dr.configName.replaceAll("/", "_") +  "_" + clazz.getName +  "_" + index
                                        )
                                }
                                
                                // Notify the monitor so we can recover from errors
                                Akka.system.actorSelection("user/TuktuMonitor") ! new ErrorIdentifierPacket(idString, actorRef)
                                
                                // Send init packet
                                actorRef ! new InitPacket
                                // Send it the config
                                actorRef ! generatorConfig
                                if (dr.returnRef) sender ! actorRef
                            }
                            catch {
                                case e: akka.actor.InvalidActorNameException => {
                                    val actorRef = {
                                        // See if this is the JS generator or not
                                        if (classOf[TuktuBaseJSGenerator].isAssignableFrom(clazz))
                                            Akka.system.actorOf(
                                                SmallestMailboxPool(instanceCount).props(
                                                    Props(clazz, dr.configName.split("/").takeRight(1).head, resultName, processorEnumeratee, dr.sourceActor)
                                                ),
                                                name = dr.configName.replaceAll("/", "_") +  "_" + clazz.getName +  "_" + index
                                            )
                                        else
                                            Akka.system.actorOf(
                                                SmallestMailboxPool(instanceCount).props(
                                                    Props(clazz, resultName, processorEnumeratee, dr.sourceActor)
                                                ),
                                                name = dr.configName.replaceAll("/", "_") + "_" + clazz.getName +  "_" + java.util.UUID.randomUUID.toString
                                            )
                                    }
                                    
                                    // Notify the monitor so we can recover from errors
                                    Akka.system.actorSelection("user/TuktuMonitor") ! new ErrorIdentifierPacket(idString, actorRef)
                                    
                                    // Send init packet
                                    actorRef ! new InitPacket
                                    // Send it the config
                                    actorRef ! generatorConfig
                                    if (dr.returnRef) sender ! actorRef
                                }
                            }
                        }
                    }
                }
            }
        }
        case _ => {}
    }
}