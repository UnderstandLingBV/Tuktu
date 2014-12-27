package controllers

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import tuktu.api.DataPacket
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import akka.actor.ActorRef
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.MPType
import tuktu.api._

case class asyncDispatchRequest(
        configName: String,
        config: Option[JsValue],
        isRemote: Boolean,
        returnRef: Boolean
)
case class syncDispatchRequest(
        configName: String,
        config: Option[JsValue],
        isRemote: Boolean,
        returnRef: Boolean
)
case class treeNode(
        name: String,
        parents: List[Class[_]],
        children: List[Class[_]]
)

class Dispatcher(monitorActor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(10 seconds)
    
    val configRepo = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
    val homeAddress = Play.current.configuration.getString("akka.remote.netty.tcp.hostname").getOrElse("127.0.0.1")
    val logLevel = Play.current.configuration.getString("tuktu.monitor.level").getOrElse("all")
    val clusterNodes = {
        Play.current.configuration.getConfigList("tuktu.cluster.nodes") match {
            case Some(nodeList) => {
                // Get all the nodes in the list and put them in a map
                (for (node <- nodeList.asScala) yield {
                    node.getString("host").getOrElse("") -> node.getString("port").getOrElse("")
                }).toMap
            }
            case None => Map[String, String]()
        }
    }
    
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
        Enumeratee.map(data => {
            monitorActor ! new MonitorPacket(mpType, generatorName, branch, data.data.size)
            data
        })
    }

    /**
     * Takes a list of Enumeratees and iteratively composes them to form a single Enumeratee
     * @param nextId The names of the processors next to compose
     * @param processorMap A map cntaining the names of the processors and the processors themselves
     */
    def buildEnums (
            nextId: List[String],
            processorMap: Map[String, (Enumeratee[DataPacket, DataPacket], List[String])],
            monitorName: String
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
                    case id::List() => {
                        // This is just a pipeline, get the processor
                        val proc = processorMap(id)
                        
                        buildEnumsHelper(proc._2, {
                            if (accum.isEmpty) List(proc._1)
                            else accum.map(enum => enum compose proc._1)
                        }, iterationCount + 1)
                    }
                    case nextList => {
                        // We have a branch here and need to expand the list of processors
                        (for (id <- nextList) yield {
                            val proc = processorMap(id)
                            
                            buildEnumsHelper(proc._2, {
        	                    if (accum.isEmpty) List(proc._1)
        	                    else accum.map(enum => enum compose proc._1)
        	                }, iterationCount + 1)
                        }).flatten
                    }
                }
            }
        }
        
        // Determine logging strategy and build the enumeratee
        logLevel match {
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
    
    def receive() = {
        case dr: asyncDispatchRequest => {
            // Get config
            val config = dr.config match {
                case Some(cfg) => cfg
                case None => {
                    val configFile = scala.io.Source.fromFile(configRepo + "/" + dr.configName + ".json", "utf-8")
		            val cfg = Json.parse(configFile.mkString).as[JsObject]
		            configFile.close
		            cfg
                }
            }

            // Get the data generators
            val generators = (config \ "generators").as[List[JsObject]]
            for ((generator, index) <- generators.zipWithIndex) {
                // Get all fields
                val generatorName = (generator \ "name").as[String]
                val generatorConfig = (generator \ "config").as[JsObject]
                val resultName = (generator \ "result").as[String]
                val next = (generator \ "next").as[List[String]]
                val nodeAddress = (generator \ "node").asOpt[String]

                // See if this one needs to be started remotely or not
                val (startRemotely, hostname) = nodeAddress match {
                    case Some(remoteLocation) => {
                        // We may or may not need to start remotely
                        if (remoteLocation == homeAddress) (false, "")
                        else (true, remoteLocation)
                    }
                    case None => (false, "")
                }
                        
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
                            val refFut = remoteDispatcher ? new asyncDispatchRequest(dr.configName, Some(config), true, dr.returnRef)
                            sender ? Await.result(refFut.mapTo[ActorRef], 5 seconds)
                        }
                        case false => remoteDispatcher ! new asyncDispatchRequest(dr.configName, Some(config), true, dr.returnRef)
                    }
                } else {
                    if (!startRemotely) {
                        // Start local actor, get all data processors
                        val processors = (config \ "processors").as[List[JsObject]]
                        val processorMap = (for (processor <- processors) yield {
                            // Get all fields
                            val processorId = (processor \ "id").as[String]
                            val processorName = (processor \ "name").as[String]
                            val processorConfig = (processor \ "config").as[JsObject]
                            val resultName = (processor \ "result").as[String]
                            val next = (processor \ "next").as[List[String]]
                            
                            // Instantiate processor
                            val procClazz = Class.forName(processorName)
                            val iClazz = procClazz.getConstructor(classOf[String]).newInstance(resultName)
                            val method = procClazz.getDeclaredMethods.filter(m => m.getName == "processor").head
                            val proc = method.invoke(iClazz, processorConfig).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
                            
                            // Return map
                            processorId -> (proc, next)
                        }).toMap
                        
                        // Build the processor pipeline for this generator
                        val processorEnumeratee = buildEnums(next, processorMap, dr.configName + "/" + generatorName)
                        
                        // Set up the generator, we assume the class is loaded
                        val clazz = Class.forName(generatorName)
                        
                        try {
                            val actorRef = Akka.system.actorOf(Props(clazz, resultName, processorEnumeratee), name = dr.configName + clazz.getName + index)
                            // Send it the config
                            actorRef ! generatorConfig
                            if (dr.returnRef) sender ! actorRef
                        }
                        catch {
                            case e: akka.actor.InvalidActorNameException => {
                                val actorRef = Akka.system.actorOf(Props(clazz, resultName, processorEnumeratee), name = dr.configName + clazz.getName + java.util.UUID.randomUUID.toString)
                                // Send it the config
                                actorRef ! generatorConfig
                                if (dr.returnRef) sender ! actorRef
                            }
                        }
                    }
                }
            }
        }
        case dr: syncDispatchRequest => {
            // Get config
            val config = dr.config match {
                case Some(cfg) => cfg
                case None => {
                    val configFile = scala.io.Source.fromFile(configRepo + "/" + dr.configName + ".json", "utf-8")
		            val cfg = Json.parse(configFile.mkString).as[JsObject]
		            configFile.close
		            cfg
                }
            }
            
            // Go through all entries in config and get the futures
            {
                // Get the data generator
                val generator = (config \ "generators").as[List[JsObject]].head
                val generatorName = (generator \ "name").as[String]
                val resultName = (generator \ "result").as[String]
                val generatorConfig = (generator \ "config").as[JsObject]
	            val next = (generator \ "next").as[List[String]]
                val nodeAddress = (generator \ "node").asOpt[String]
                val remoteTimeout = (generator \ "timeout").asOpt[Int].getOrElse(10)
                
                // Set up the generator, we assume the class is loaded
                val clazz = Class.forName(generatorName)
                
                // See if this one needs to be started remotely or not
                val (startRemotely, hostname) = nodeAddress match {
                    case Some(remoteLocation) => {
                        // We may or may not need to start remotely
                        if (remoteLocation == homeAddress) (false, "")
                        else (true, remoteLocation)
                    }
                    case None => (false, "")
                }
                
                if (startRemotely && !dr.isRemote  && hostname != "" && clusterNodes.contains(hostname)) {
                    // We need to start an actor on a remote location
                    val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname) + "/user/TuktuDispatcher"
                    // Get the identity
                    val fut = Akka.system.actorSelection(location) ? Identify(None)
                    val remoteDispatcher = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
                    
                    // Send a remoted dispatch request, which is just the obtained config
                    dr.returnRef match {
                        case true => {
                            // We must return the actorRef of the synchronous generator
                            val refFut = remoteDispatcher ? new syncDispatchRequest(dr.configName, Some(config), true, true)
                            refFut.onSuccess {
                                case ar: ActorRef => sender ! ar
                            }
                        }
                        case false => sender ! (remoteDispatcher ? new syncDispatchRequest(dr.configName, Some(config), true, false)).asInstanceOf[Future[Enumerator[DataPacket]]]
                    }
                } else {
                    if (!startRemotely) {
                        // Get all data processors
                        val processors = (config \ "processors").as[List[JsObject]]
                        val processorMap = (for (processor <- processors) yield {
                            // Get all fields
                            val processorId = (processor \ "id").as[String]
                            val processorName = (processor \ "name").as[String]
                            val processorConfig = (processor \ "config").as[JsObject]
                            val resultName = (processor \ "result").as[String]
                            val next = (processor \ "next").as[List[String]]
                            
                            // Instantiate processor
                            val procClazz = Class.forName(processorName)
                            val iClazz = procClazz.getConstructor(classOf[String]).newInstance(resultName)
                            val method = procClazz.getDeclaredMethods.filter(m => m.getName == "processor").head
                            val proc = method.invoke(iClazz, processorConfig).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
                            
                            // Return map
                            processorId -> (proc, next)
                        }).toMap
                        
                        // Build the processor pipeline for this generator
                        val processorEnumeratee = buildEnums(next, processorMap, dr.configName  + "/" + generatorName)

                        try {
                        	val actorRef = Akka.system.actorOf(Props(clazz, resultName, processorEnumeratee), name = dr.configName + clazz.getName)

                        	// Send it the config
                            dr.returnRef match {
                                case true => sender ! actorRef
                                case false => sender ! (actorRef ? generatorConfig).asInstanceOf[Future[Enumerator[DataPacket]]]
                            }
                        } catch {
                            case e: akka.actor.InvalidActorNameException => {
                                val actorRef = Akka.system.actorOf(Props(clazz, resultName, processorEnumeratee), name = dr.configName + clazz.getName +  java.util.UUID.randomUUID.toString)
                                // Send it the config
                                dr.returnRef match {
                                    case true => sender ! actorRef
                                    case false => sender ! (actorRef ? generatorConfig).asInstanceOf[Future[Enumerator[DataPacket]]]
                                }
                            }
                        }
                    } else sender ! null
                }
            }
        }
        case _ => {}
    }
}