package controllers.restapi

import java.nio.file.Files
import java.nio.file.Paths

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{ Try, Success, Failure }

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api._

/**
 * Controller for all REST API functionality of Tuktu
 */
object RESTAPI extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    /**
     * Shows API definitions and endpoints
     */
    def index() = Action {
        val url = Cache.getAs[String]("web.url").getOrElse("http://localhost:9000")
        val prefix = Play.current.configuration.getString("tuktu.restapi.prefix").getOrElse("api")
        val version = Play.current.configuration.getString("tuktu.restapi.version").getOrElse("version")
        // Get the routes
        val routes = {
            val rts = Play.current.routes map (routes => routes.documentation) getOrElse (Nil)
            for {
                r <- rts
                if (r._3.startsWith("controllers.restapi"))
            } yield Json.obj(
                    "url" -> r._2,
                    "method" -> r._1
            )
        }

        Ok(Json.obj(
                "api_base_url" -> url,
                "api_prefix" -> prefix,
                "api_version" -> version,
                "api_endpoints" -> routes
        ))
    }

    /**
     * List all jobs, running and finished
     */
    def getJobs() = Action.async {
        // Ask the monitor for all jobs
        val fut = (Akka.system.actorSelection("user/TuktuMonitor") ? new MonitorOverviewRequest).asInstanceOf[Future[MonitorOverviewResult]]
        fut.map {
            case mor: MonitorOverviewResult => {
                // TODO: Extend with more fields per flow
                Ok(Json.obj(
                        "running" -> mor.runningJobs.map { case (_, job) => 
                            Json.obj(
                                    "uuid" -> job.uuid,
                                    "config_name" -> job.configName,
                                    "instance" -> job.instances,
                                    "finished_instances" -> job.finished_instances,
                                    "start_time" -> job.startTime,
                                    "end_time" -> job.endTime.getOrElse(null).asInstanceOf[Long]
                            )
                        },
                        "finished" -> mor.runningJobs.map { case (_, job) => 
                            Json.obj(
                                    "uuid" -> job.uuid,
                                    "config_name" -> job.configName,
                                    "instance" -> job.instances,
                                    "finished_instances" -> job.finished_instances,
                                    "start_time" -> job.startTime,
                                    "end_time" -> job.endTime.getOrElse(null).asInstanceOf[Long]
                            )
                        }
                ))
            }
        }
    }

    /**
     * Gets the config for a specific job
     */
    def getConfig(name: String) = Action.async { Future {
        utils.loadConfig(name) match {
            case Success(obj) => Ok(obj)
            case Failure(e: java.io.FileNotFoundException) => NotFound(Json.obj("error" -> "The specified config file could not be found."))
            case Failure(e) => BadRequest(Json.obj("error" -> e.getMessage))
        }
    }}

    /**
     * Sets a config
     */
    def setConfig(name: String) = Action.async { request =>
        try Future {
            // Get the config from POST
            val config = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]

            // Store it
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            val withEnding = if (name.endsWith(".json")) name else name + ".json"
            // Check if absolute normalized path starts with configs repo and new file doesnt exist yet
            val path = Paths.get(configsRepo, withEnding).toAbsolutePath.normalize
            if (!path.startsWith(configsPath))
                NotFound(Json.obj("error" -> "Invalid path/name found."))
            else if (Files.exists(path))
                BadRequest(Json.obj("error" -> "File name already exists."))
            else {
                try {
                    Files.createDirectories(path.getParent)
                    Files.write(path, Json.prettyPrint(config).getBytes("utf-8"))
                    Ok("")
                } catch {
                    case e: Throwable => BadRequest(Json.obj("error" -> e.getMessage))
                }
            }
        } catch {
            case e: Throwable => Future.successful(BadRequest(
                Json.obj("error" -> e.getMessage)))
        }
    }

    /**
     * Removes an existing config files
     */
    def removeConfig(name: String) = Action.async { request =>
        try Future {
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            // Check if absolute normalized path starts with config repo and if file exists
            val withEnding = if (name.endsWith(".json")) name else name + ".json"
            val path = Paths.get(configsRepo, withEnding).toAbsolutePath.normalize
            if (!path.startsWith(configsPath))
                BadRequest(Json.obj("error" -> "Invalid path/name found."))
            else if (!Files.exists(path))
                NotFound(Json.obj("error" -> "The specified config file could not be found."))
            else {
                Files.delete(path)
                Ok("")
            }
        } catch {
            case e: Exception => Future.successful(BadRequest(
                Json.obj("error" -> e.getMessage)))
        }
    }

    /**
     * Helps in setting up a job and streaming the result back
     */
    class jobHelperActor(name: String, config: Option[JsObject], sendData: Option[JsObject], terminate: Boolean) extends Actor with ActorLogging {
        val (enum, channel) = Concurrent.broadcast[JsObject]

        def receive() = {
            case ip: InitPacket => {
                val generator = (Akka.system.actorSelection("user/TuktuDispatcher") ?
                        new DispatchRequest(name, config, false, true, true, Some(self))).asInstanceOf[Future[ActorRef]]
                generator.map {
                    case gen: ActorRef => {
                        sendData match {
                            case None => {}
                            case Some(data) => {
                                gen ! DataPacket(List(utils.JsObjectToMap(data)))
                                if (terminate)
                                    gen ! new StopPacket()
                            }
                        }
                    }
                }
                sender ! enum
            }
            case dp: DataPacket => {
                dp.data.foreach(datum => channel.push(utils.MapToJsObject(datum, false)))
            }
            case sp: StopPacket => {
                channel.eofAndEnd()
                self ! PoisonPill
            }
        }
    }

    /**
     * Starts a job, either by config name, or by provided config file as POST body
     */
    def start() = Action.async { request =>
        try {
            // Get job name from post body, or config if there is no name
            val postBody = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]
            // Should we return the result of this flow or not?
            val returnResult = (postBody \ "result").asOpt[Boolean].getOrElse(false)

            // Handle config
            val config = if (postBody.keys.contains("name") && !postBody.keys.contains("config"))
                    None
                else if (postBody.keys.contains("name") && postBody.keys.contains("config"))
                    Some((postBody \ "config").as[JsObject])
                else null

            // See if we need to send data to the actor
            val sendData = if (postBody.keys.contains("data")) {
                Some((postBody \ "data").as[JsObject])
            } else None
            
            // Terminate after sending the data or not?
            val sendEof = (postBody \ "terminate").asOpt[Boolean].getOrElse(false)

            if (config == null) Future { BadRequest(Json.obj("error" -> "Bad POST body")) }
            else {
                // Determine if we need to wait for the result or not
                if (returnResult) {
                    // Set up helper actor
                    val helper = Akka.system.actorOf(Props(classOf[jobHelperActor],
                            (postBody \ "name").as[String], config, sendData, sendEof), java.util.UUID.randomUUID.toString)
                    val fut = (helper ? new InitPacket).asInstanceOf[Future[Enumerator[JsObject]]]

                    fut.map(enum =>
                        // Stream the results
                        Ok.chunked(enum)
                    )
                } else {
                    sendData match {
                        case None => {
                            Akka.system.actorSelection("user/TuktuDispatcher") ! new DispatchRequest((postBody \ "name").as[String], config, false, false, false, None)
                            Future { Ok("") }
                        }
                        case Some(data) => {
                            // We need to send the generator data to kick it off apparently
                            val gen = Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest((postBody \ "name").as[String], config, false, true, false, None)
                            gen.map {
                                case g: ActorRef => {
                                    g ! DataPacket(List(utils.JsObjectToMap(data)))
                                    Ok("")
                                }
                                case _: Any => BadRequest(Json.obj("error" -> "Couldn't obtain generator from Dispatcher"))
                            }
                        }
                    }
                }
            }
        }
        catch {
            case e: Throwable => Future { BadRequest(Json.obj("error" -> e.getMessage)) }
        }
    }

    /**
     * Stops a job (gracefully)
     */
    def stop() = Action.async { request =>
        // We need the job's UUID
        val postBody = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]
        val uuid = (postBody \ "uuid").as[String]

        // Monitor stops jobs
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(uuid, "stop")
        Future { Ok("") }
    }
    
    /**
     * Stops jobs based on a config name
     */
    def stopByName() = Action.async { request =>
        // Get the config name
        val name = (request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject] \ "name").as[String]
        
        val monActor = Akka.system.actorSelection("user/TuktuMonitor")
        
        // Get the monitoring overview
        val fut = (monActor ? new MonitorOverviewRequest()).asInstanceOf[Future[MonitorOverviewResult]]
        fut.map{flows =>
            // Filter out the ones we need
            val flowsToStop = flows.runningJobs.filter(flow => flow._2.configName == name).map(_._1)
            
            // Stop them 1 by 1
            flowsToStop.foreach(flow => monActor ! new AppMonitorUUIDPacket(flow, "stop"))
            
            Ok(Json.obj(
                    "stoppped_flows" -> Json.arr(flowsToStop)
            ))
        }
    }

    /**
     * Terminates a job (ungracefully)
     */
    def terminate() = Action.async { request =>
        // We need the job's UUID
        val postBody = request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject]
        val uuid = (postBody \ "uuid").as[String]

        // Monitor stops jobs
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(uuid, "kill")
        Future { Ok("") }
    }
    
    /**
     * Terminates jobs by name
     */
    def terminateByName() = Action.async { request =>
        // Get the config name
        val name = (request.body.asJson.getOrElse(Json.obj()).asInstanceOf[JsObject] \ "name").as[String]
        
        val monActor = Akka.system.actorSelection("user/TuktuMonitor")
        
        // Get the monitoring overview
        val fut = (monActor ? new MonitorOverviewRequest()).asInstanceOf[Future[MonitorOverviewResult]]
        fut.map{flows =>
            // Filter out the ones we need
            val flowsToStop = flows.runningJobs.filter(flow => flow._2.configName == name).map(_._1)
            
            // Stop them 1 by 1
            flowsToStop.foreach(flow => monActor ! new AppMonitorUUIDPacket(flow, "kill"))
            
            Ok(Json.obj(
                    "stoppped_flows" -> Json.arr(flowsToStop)
            ))
        }
    }
}