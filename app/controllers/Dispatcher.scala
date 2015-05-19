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
import play.api.Logger
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api._
import tuktu.generators.AsyncStreamGenerator
import tuktu.generators.EOFSyncStreamGenerator
import tuktu.generators.SyncStreamGenerator
import tuktu.processors.EOFBufferProcessor
import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor

case class DispatchRequest(
        configName: String,
        config: Option[JsValue],
        isRemote: Boolean,
        returnRef: Boolean,
        sync: Boolean,
        sourceActor: Option[ActorRef]
)
case class treeNode(
        name: String,
        parents: List[Class[_]],
        children: List[Class[_]]
)

object Dispatcher {
    /**
     * Enumeratee for error-logging and handling
     */
    def logEnumeratee[T] = Enumeratee.recover[T] {
        case (e, input) => Logger.error("Error happened on: " + input, e)
    }
    
    /**
     * Monitoring enumeratee
     */
    def monitorEnumeratee(generatorName: String, branch: String, mpType: MPType): Enumeratee[DataPacket, DataPacket] = {
        implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        
        // Get the monitoring actor
        /*val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
        val monitorActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef*/
        
        Enumeratee.mapM(data => {
            if (data.data.size > 0)
                Akka.system.actorSelection("user/TuktuMonitor") ! new MonitorPacket(mpType, generatorName, branch, data.data.size)
            Future {data}
        })
    }
    
    /**
     * Takes a list of Enumeratees and iteratively composes them to form a single Enumeratee
     * @param nextId The names of the processors next to compose
     * @param processorMap A map cntaining the names of the processors and the processors themselves
     */
    def buildEnums (
            nextId: List[String],
            processorMap: Map[String, ProcessorDefinition],
            monitorName: String,
            genActor: Option[ActorRef]
    ): List[Enumeratee[DataPacket, DataPacket]] = {
        /**
         * Function that recursively builds the tree of processors
         */
        def buildEnumsHelper(
                next: List[String],
                accum: List[Enumeratee[DataPacket, DataPacket]],
                iterationCount: Integer
        ): List[Enumeratee[DataPacket, DataPacket]] = {
            if (iterationCount > 500) {
                // Awful lot of enumeratees... cycle?
                throw new Exception("Possible cycle detected in config file. Aborted")
            }
            else {
                next match {
                    case List() => {
                        // We are done, return accumulator
                        accum
                    }
                    case nextList => {
                        // We have a branch here and need to expand the list of processors
                        (for (id <- nextList) yield {
                            val pd = processorMap(id)
                            
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
                                                buildEnumsHelper(pd.next, List(logEnumeratee[DataPacket]), iterationCount + 1),
                                                genActor
                                            ))
                                        } else {
                                            Akka.system.actorOf(Props(classOf[tuktu.generators.SyncStreamGenerator], "",
                                                buildEnumsHelper(pd.next, List(logEnumeratee[DataPacket]), iterationCount + 1),
                                                {
                                                    if (classOf[BufferProcessor].isAssignableFrom(procClazz)) genActor
                                                    else None
                                                }
                                            ))
                                        }
                                    }
                                    case false => {
                                        Akka.system.actorOf(Props(classOf[tuktu.generators.AsyncStreamGenerator], "",
                                            buildEnumsHelper(pd.next, List(logEnumeratee[DataPacket]), iterationCount + 1),
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
                                try {
                                    val initMethod = procClazz.getDeclaredMethods.filter(m => m.getName == "initialize").head
                                    initMethod.invoke(iClazz, pd.config)
                                } catch {
                                    case e: NoSuchElementException => {}
                                    case e: Exception => e.printStackTrace()
                                }
                                
                                // Add method to all our entries so far
                                val method = procClazz.getDeclaredMethods.filter(m => m.getName == "processor").head
                                accum.map(enum => enum compose method.invoke(iClazz).asInstanceOf[Enumeratee[DataPacket, DataPacket]])
                            }
                            else {
                                val iClazz = procClazz.getConstructor(classOf[String]).newInstance(pd.resultName)
                            
                                // Initialize the processor first
                                try {
                                    val initMethod = procClazz.getDeclaredMethods.filter(m => m.getName == "initialize").head
                                    initMethod.invoke(iClazz, pd.config)
                                } catch {
                                    case e: NoSuchElementException => {}
                                }
                                
                                val method = procClazz.getDeclaredMethods.filter(m => m.getName == "processor").head
                                val procEnum = method.invoke(iClazz).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
                                
                                // Recurse
                                buildEnumsHelper(pd.next, accum.map(enum => enum compose procEnum), iterationCount + 1)
                            }
                        }).flatten
                    }
                }
            }
        }
        
        // Determine logging strategy and build the enumeratee
        Cache.getAs[String]("logLevel").getOrElse("all") match {
            case "all" => {
                val enums = buildEnumsHelper(nextId, List(logEnumeratee[DataPacket]), 0)
                for ((enum, index) <- enums.zipWithIndex) yield {
                    monitorEnumeratee(monitorName, index.toString, BeginType) compose
                    enum compose
                    monitorEnumeratee(monitorName, index.toString, EndType)
                }
            }
            case "none" => buildEnumsHelper(nextId, List(logEnumeratee[DataPacket]), 0)
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
        Play.current.configuration.getConfigList("tuktu.cluster.nodes") match {
            case Some(nodeList) => {
                // Get all the nodes in the list and put them in a map
                (for (node <- nodeList.asScala) yield {
                    node.getString("host").getOrElse("") -> node.getString("port").getOrElse("")
                }).toMap
            }
            case None => Map[String, String]()
        }
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
                            
                    val clusterNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map[String, String]())
                    if (startRemotely && !dr.isRemote && hostname != "" && clusterNodes.contains(hostname)) {
                        // We need to start an actor on a remote location
                        val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname) + "/user/TuktuDispatcher"
                        
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
                            val processorEnumeratee = Dispatcher.buildEnums(next, processorMap, dr.configName + "/" + generatorName, dr.sourceActor)
                            
                            // Set up the generator, we assume the class is loaded
                            val clazz = Class.forName(generatorName)
                            
                            try {
                                // Make the amount of actors we require
                                val actorRef = Akka.system.actorOf(
                                        SmallestMailboxPool(instanceCount).props(
                                            Props(clazz, resultName, processorEnumeratee, dr.sourceActor)
                                        ),
                                        name = dr.configName +  "_" + clazz.getName +  "_" + index
                                )
                                
                                // Send it the config
                                actorRef ! generatorConfig
                                if (dr.returnRef) sender ! actorRef
                                
                                // Send the monitoring actor notification of start
                                Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                                        actorRef.path.toStringWithoutAddress,
                                        System.currentTimeMillis() / 1000L,
                                        "start"
                                )
                            }
                            catch {
                                case e: akka.actor.InvalidActorNameException => {
                                    val actorRef = Akka.system.actorOf(
                                            SmallestMailboxPool(instanceCount).props(
                                                Props(clazz, resultName, processorEnumeratee, dr.sourceActor)
                                            ),
                                            name = dr.configName + "_" + clazz.getName +  "_" + java.util.UUID.randomUUID.toString
                                    )
                                    
                                    // Send it the config
                                    actorRef ! generatorConfig
                                    if (dr.returnRef) sender ! actorRef
                                    
                                    // Send the monitoring actor notification of start
                                    Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                                            actorRef.path.toStringWithoutAddress,
                                            System.currentTimeMillis() / 1000L,
                                            "start"
                                    )
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