package controllers.restapi

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api._
import java.nio.file.Paths
import java.nio.file.Files
import scala.concurrent.duration.Duration
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Concurrent
import akka.actor.PoisonPill
import play.api.libs.iteratee.Input

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
                        "running" -> mor.runningJobs.map(job => {
                            Json.obj(
                                    "uuid" -> job._2.uuid,
                                    "instance" -> job._2.instances,
                                    "start_time" -> job._2.startTime,
                                    "finished_instances" -> job._2.finished_instances,
                                    "end_time" -> job._2.endTime.getOrElse(null).asInstanceOf[Long]
                            )
                        }),
                        "finished" -> mor.finishedJobs.map(job => {
                            Json.obj(
                                    "uuid" -> job._2.uuid,
                                    "instance" -> job._2.instances,
                                    "start_time" -> job._2.startTime,
                                    "finished_instances" -> job._2.finished_instances,
                                    "end_time" -> job._2.endTime.getOrElse(null).asInstanceOf[Long]
                            )
                        })
                ))
            }
        }
    }
    
    /**
     * Gets the config for a specific job
     */
    def getConfig(name: String) = Action.async { Future {
        // Read config from disk
        val config = {
            val configFile = scala.io.Source.fromFile(Cache.getAs[String]("configRepo").getOrElse("configs") +
                    "/" + name + ".json", "utf-8")
            val cfg = Json.parse(configFile.mkString).as[JsObject]
            configFile.close
            cfg
        }
        
        Ok(config)
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
            Files.createDirectories(path)
            if (!path.startsWith(configsPath) || Files.exists(path))
                BadRequest(Json.obj("error" -> "Invalid path/name found."))
            else {
                try {
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
     * Helps in setting up a job and streaming the result back
     */
    class jobHelperActor(name: String, config: Option[JsObject], sendData: Option[JsObject]) extends Actor with ActorLogging {
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
                                gen ! new DataPacket(List(utils.JsObjectToMap(data)))
                                gen ! Input.EOF
                            }
                        }
                    }
                }
                sender ! enum
            }
            case dp: DataPacket => dp.data.foreach(datum => channel.push(utils.MapToJsObject(datum, false)))
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
            
            if (config == null) Future { BadRequest(Json.obj("error" -> "Bad POST body")) }
            else {
                // Determine if we need to wait for the result or not
                if (returnResult) {
                    // Set up helper actor
                    val helper = Akka.system.actorOf(Props(classOf[jobHelperActor],
                            (postBody \ "name").as[String], config, sendData), java.util.UUID.randomUUID.toString)
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
                                    g ! new DataPacket(List(utils.JsObjectToMap(data)))
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
}