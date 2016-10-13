package controllers

import java.nio.file.{ Path, Paths, Files }
import scala.collection.JavaConversions.{ seqAsJavaList, asScalaBuffer }
import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.concurrent.Akka
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api._
import tuktu.utils.util
import play.api.cache.Cache
import play.api.libs.json.Json
import java.io.File
import play.api.libs.json.JsObject

object Monitor extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Fetches the monitor's info
     */
    def fetchLocalInfo(runningPage: Int = 1, finishedPage: Int = 1) = Action.async { implicit request =>
        // Get the monitor overview result
        val fut = (Akka.system.actorSelection("user/TuktuMonitor") ? new MonitorOverviewRequest).asInstanceOf[Future[MonitorOverviewResult]]
        fut.map(res => {
            val sortedRunningJobs = res.runningJobs.toList.sortWith((a,b) => a._2.startTime > b._2.startTime)
            val sortedFinishedJobs = res.finishedJobs.toList.sortWith((a,b) => a._2.endTime.getOrElse(a._2.startTime) > b._2.endTime.getOrElse(b._2.startTime))
            // If we are out of range, go back into range
            if (runningPage < 1 || runningPage > math.max(math.ceil(sortedRunningJobs.size / 100.0), 1) || finishedPage < 1 || finishedPage > math.max(math.ceil(sortedFinishedJobs.size / 100.0), 1))
                Redirect(routes.Monitor.fetchLocalInfo(
                    math.max(math.min(runningPage, math.ceil(sortedRunningJobs.size / 100.0).toInt), 1),
                    math.max(math.min(finishedPage, math.ceil(sortedFinishedJobs.size / 100.0).toInt), 1)))
            else
                Ok(views.html.monitor.showApps(
                    runningPage,
                    finishedPage,
                    sortedRunningJobs,
                    sortedFinishedJobs,
                    res.subflows,
                    util.flashMessagesToMap(request)))
        })
    }

    /**
     * Clears the monitor's info about finished flows
     */
    def clearFinished(runningPage: Int = 1, finishedPage: Int = 1) = Action { implicit request =>
        Akka.system.actorSelection("user/TuktuMonitor") ! "clearFinished"
        Redirect(routes.Monitor.fetchLocalInfo(runningPage, finishedPage))
    }

    /**
     * Clears the monitor's info about flows that encountered errors
     */
    def clearErrors(runningPage: Int = 1, finishedPage: Int = 1) = Action { implicit request =>
        Akka.system.actorSelection("user/TuktuMonitor") ! "clearErrors"
        Redirect(routes.Monitor.fetchLocalInfo(runningPage, finishedPage))
    }

    /**
     * Clears the monitor's info about a flow given by uuid
     */
    def clear(id: String, runningPage: Int = 1, finishedPage: Int = 1) = Action { implicit request =>
        Akka.system.actorSelection("user/TuktuMonitor") ! ClearFlowPacket(id)
        Redirect(routes.Monitor.fetchLocalInfo(runningPage, finishedPage))
    }

    /**
     * Gets the last received and processed DataPacket for a processor_id and a flow
     */
    def getLastDataPacket(flow_name: String, processor_id: String) = Action.async { implicit request =>
        val fut = (Akka.system.actorSelection("user/TuktuMonitor") ? new MonitorLastDataPacketRequest(flow_name, processor_id)).asInstanceOf[Future[(String, String)]]
        fut.map(res => {
            Ok(Json.arr(res._1, res._2))
        })
    }

    /**
     * Terminates a Tuktu job
     */
    def terminate(uuid: String, force: Boolean, runningPage: Int = 1, finishedPage: Int = 1) = Action {
        // Inform the monitor since the generator won't do it itself
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(
            uuid,
            if (force) "kill" else "stop")

        Redirect(routes.Monitor.fetchLocalInfo(runningPage, finishedPage)).flashing("success" -> ("Successfully " + {
            force match {
                case true => "terminated"
                case _    => "stopped"
            }
        } + " job " + uuid))
    }

    /**
     * Shows the form for getting configs
     */
    def showConfigs() = Action { implicit request =>
        {
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            // Create configs folder if it doesn't exist
            if (!Files.isDirectory(configsPath))
                Files.createDirectories(configsPath)

            // Get file path from the body
            val body = request.body.asFormUrlEncoded.getOrElse(Map.empty)
            val file = body("path").headOption.getOrElse("")
            val filePath = Paths.get(configsRepo, file).toAbsolutePath.normalize

            // Check if file path is subpath of configs repo, or default to config repo
            val path = if (filePath.startsWith(configsPath) && Files.isDirectory(filePath)) filePath else configsPath
            val relativePath = configsPath.relativize(path)
            val pathSeq = (for (i <- 0 until relativePath.getNameCount) yield relativePath.getName(i).toString).filter(_.nonEmpty)

            // Define collector and partition files and directories
            val collector = java.util.stream.Collectors.groupingBy[Path, Boolean](
                new java.util.function.Function[Path, Boolean] {
                    def apply(path: Path): Boolean = Files.isDirectory(path)
                })

            if (Files.isDirectory(path)) {
                val stream = Files.list(path)
                val map = stream.collect(collector)
                stream.close

                // Get configs
                val configs = map.getOrDefault(false, Nil).map(cfg => cfg.getFileName.toString.dropRight(5)).sortBy(_.toLowerCase)
                // Get subfolders
                val subfolders = map.getOrDefault(true, Nil).map(fldr => fldr.getFileName.toString).sortBy(_.toLowerCase)

                // Invoke view
                Ok(views.html.monitor.showConfigs(
                    pathSeq, configs, subfolders))
            } else {
                Ok(views.html.monitor.showConfigs(
                    Nil, Buffer.empty, Buffer.empty))
            }
        }
    }

    /**
     * Shows the start-job view
     */
    def browseConfigs() = Action { implicit request => {
            Ok(views.html.monitor.browseConfigs(util.flashMessagesToMap(request)))
    }}

    case class job(
        name: String)

    val jobForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1)))(job.apply)(job.unapply))

    /**
     * Actually starts a job
     */
    def startJob() = Action { implicit request =>
        {
            // Bind
            jobForm.bindFromRequest.fold(
                formWithErrors => {
                    Redirect(routes.Monitor.browseConfigs).flashing("error" -> "Invalid job name or instances")
                },
                job => {
                    Akka.system.actorSelection("user/TuktuDispatcher") ! new DispatchRequest(job.name, None, false, false, false, None)
                    Redirect(routes.Monitor.fetchLocalInfo(1, 1)).flashing("success" -> ("Successfully started job " + job.name))
                })
        }
    }

    /**
     * Creates a new JSON file
     */
    def newFile() = Action { implicit request =>
        {
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            // Get file path from the body
            val body = request.body.asFormUrlEncoded.getOrElse(Map.empty)
            body("file").headOption match {
                case None => BadRequest
                case Some(file) => {
                    val withEnding = if (file.endsWith(".json")) file else file + ".json"
                    // Check if absolute normalized path starts with configs repo and new file doesnt exist yet
                    val path = Paths.get(configsRepo, withEnding).toAbsolutePath.normalize
                    if (!path.startsWith(configsPath) || Files.exists(path))
                        BadRequest
                    else {
                        try {
                            Files.write(path, Json.prettyPrint(Json.obj("generators" -> Json.arr(), "processors" -> Json.arr())).getBytes("utf-8"))
                            Ok
                        } catch {
                            case _: Throwable => BadRequest
                        }
                    }
                }
            }
        }
    }

    /**
     * Uploads a JSON config file to a specific folder
     */
    def uploadFile() = Action(parse.multipartFormData) { request =>
        request.body.file("fileName").map { fName =>
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize
            
            // Get file
            val filename = request.body.asFormUrlEncoded("path").headOption.getOrElse("") + "/" + fName.filename
            val contentType = fName.contentType
            
            // Get path, determine proper name
            val withEnding = if (filename.endsWith(".json")) filename else filename + ".json"
            
            // Check if absolute normalized path starts with configs repo and new file doesnt exist yet
            val path = Paths.get(configsRepo, withEnding).toAbsolutePath.normalize
            if (!path.startsWith(configsPath) || Files.exists(path))
                Redirect(routes.Monitor.browseConfigs).flashing("error" -> ("Failed to upload config - " + {
                    if (!path.startsWith(configsPath)) "Invalid repository path found"
                    else "File already exists"
                }))
            else {
                // First move the file
                fName.ref.moveTo(new File(path.toString))
                
                // Check for validity
                val valid = try {
                    // Parse JSON into a JSObject
                    val json = Json.parse(Files.readAllLines(path).mkString("")).as[JsObject]
                    // Check if generators and processors are there
                    val generators = (json \ "generators").as[List[JsObject]]
                    val processors = (json \ "processors").as[List[JsObject]]
                    true
                }
                catch {
                    case e: Exception => false
                }
                
                // Return properly, or not
                if (valid) Redirect(routes.Monitor.browseConfigs)
                else {
                    // Remove file
                    Files.delete(path)
                    Redirect(routes.Monitor.browseConfigs).flashing("error" -> "Uploaded file is not a valid config file")
                }
            }
        }.getOrElse { Redirect(routes.Monitor.browseConfigs).flashing("error" -> "Failed to upload config") }
    }

    /**
     * Creates a new directory
     */
    def newDirectory() = Action { implicit request =>
        {
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            // Get file path from the body
            val body = request.body.asFormUrlEncoded.getOrElse(Map.empty)
            body("path").headOption match {
                case None => BadRequest
                case Some(dir) => {
                    // Check if absolute normalized path starts with configs repo and new dir doesnt exist yet
                    val path = Paths.get(configsRepo, dir).toAbsolutePath.normalize
                    if (!path.startsWith(configsPath) || Files.exists(path))
                        BadRequest
                    else {
                        try {
                            Files.createDirectory(path)
                            Ok
                        } catch {
                            case _: Throwable => BadRequest
                        }
                    }
                }
            }
        }
    }

    /**
     * Deletes a file from the config repository
     */
    def deleteFile() = Action { implicit request =>
        {
            // Get config repo
            val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
            val configsPath = Paths.get(configsRepo).toAbsolutePath.normalize

            // Get file path from the body
            val body = request.body.asFormUrlEncoded.getOrElse(Map.empty)
            body("file").headOption match {
                case None => BadRequest
                case Some(file) => {
                    // Check if absolute normalized path starts with configs repo and is a file
                    val path = Paths.get(configsRepo, file).toAbsolutePath.normalize
                    if (!path.startsWith(configsPath) || !Files.isRegularFile(path))
                        BadRequest
                    else {
                        try {
                            Files.delete(path)
                            Ok
                        } catch {
                            case _: Throwable => BadRequest
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts multiple jobs at the same time
     */
    def batchStarter() = Action { implicit request =>
        {
            val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
            val jobs = body("jobs").head.split(",")

            // Go over them and start them
            val dispatcher = Akka.system.actorSelection("user/TuktuDispatcher")
            jobs.foreach(job => dispatcher ! new DispatchRequest(job, None, false, false, false, None))

            Ok("")
        }
    }
}