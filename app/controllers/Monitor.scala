package controllers

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.routing.Broadcast
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
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import play.api.libs.json.Json

object Monitor extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Fetches the monitor's info
     */
    def fetchLocalInfo() = Action.async { implicit request =>
        // Get the monitor
        val fut = (Akka.system.actorSelection("user/TuktuMonitor") ? new MonitorOverviewPacket()).asInstanceOf[Future[MonitorOverviewResult]]
        fut.map(res => {
            Ok(views.html.monitor.showApps(
                    res.runningJobs.toList.sortBy(elem => elem._2.startTime),
                    res.finishedJobs.toList.sortBy(elem => elem._2._1),
                    res.monitorData,
                    res.subflows,
                    util.flashMessagesToMap(request)
            ))
        })
    }
    
    /**
     * Terminates a Tuktu job
     */
    def terminate(name: String, force: Boolean) = Action {
        // Send stop packet to actor
        if (force) {
            Akka.system.actorSelection(name) ! Broadcast(PoisonPill)

            // Inform the monitor since the generator won't do it itself
            val generatorName = Akka.system.actorSelection(name) ? Identify(None)
            generatorName.onSuccess {
                case generator: ActorRef => {
                    Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                            generator,
                            "kill"
                    )
                } 
            }
        }
        else 
            Akka.system.actorSelection(name) ! Broadcast(new StopPacket)

        Redirect(routes.Monitor.fetchLocalInfo()).flashing("success" -> ("Successfully " + {
            force match {
                case true => "terminated"
                case _ => "stopped"
            }
        } + " job " + name))
    }
    
    /**
     * Shows the form for getting configs
     */
    def showConfigs() = Action { implicit request => {
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        
        // Get the path from the body
        val path = body("path").head.split("/").filter(elem => !elem.isEmpty)
        
        // Load the files and folders from the config repository
        val configRepo = {
            val location = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
            if (location.last != '/') location + "/"
            else location
        }
        val files = new File(configRepo + path.mkString("/")).listFiles
        
        // Get configs
        val configs = files.filter(!_.isDirectory).map(cfg => cfg.getName.take(cfg.getName.size - 5))
        // Get subfolders
        val subfolders = files.filter(_.isDirectory).map(fldr => fldr.getName)
        
        // Invoke view
        Ok(views.html.monitor.showConfigs(
                path, configs, subfolders
        ))
    }}
    
    /**
     * Shows the start-job view
     */
    def browseConfigs() = Action { implicit request => {
            Ok(views.html.monitor.browseConfigs(util.flashMessagesToMap(request)))
        }
    }
    
    case class job(
        name: String
    )
    
    val jobForm = Form(
        mapping(
            "name" -> text.verifying(nonEmpty, minLength(1))
        ) (job.apply)(job.unapply)
    )
    
    /**
     * Actually starts a job
     */
    def startJob() = Action { implicit request => {
            // Bind
            jobForm.bindFromRequest.fold(
                formWithErrors => {
                    Redirect(routes.Monitor.browseConfigs).flashing("error" -> "Invalid job name or instances")
                },
                job => {
                    Akka.system.actorSelection("user/TuktuDispatcher") ! new DispatchRequest(job.name, None, false, false, false, None)
                    Redirect(routes.Monitor.fetchLocalInfo).flashing("success" -> ("Successfully started job " + job.name))
                }
            )
        }
    }
    
    /**
     * Creates a new JSON file
     */
    def newFile() = Action { implicit request => {
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        
        // Get the path and filename from the body
        val path = body("path").head
        val filename = {
            val fname = body("file").head
            if (fname.endsWith(".json")) fname
            else fname + ".json"
        }
        // Folder or file?
        val isFolder = body("folder").head.toBoolean
        
        // Get prefix
        val configRepo = {
            val location = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
            if (location.last != '/') location + "/"
            else location
        }

        if (isFolder) {
            // Create folder
            new File(configRepo + path + "/" + filename.dropRight(5)).mkdir
        }
        else {
            // Wrte default output to file
            val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configRepo + path + "/" + filename), "utf-8"))
            val output = Json.obj(
                    "generators" -> Json.arr(),
                    "processors" -> Json.arr()
            )
            writer.write(output.toString)
            writer.close
        }

        Ok("")
    }}
    
    /**
     * Deletes a file from the config repository
     */
    def deleteFile() = Action { implicit request => {
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        
        // Get the filename from the body
        val filename = {
            val fname = body("file").head
            val configRepo = {
                val location = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
                if (location.last != '/') location + "/"
                else location
            }
            configRepo + fname
        }
        
        // Remove it
        new File(filename).delete
        
        Ok("")
    }}
    
    /**
     * Starts multiple jobs at the same time
     */
    def batchStarter() = Action { implicit request => {
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        val jobs = body("jobs").head.split(",")
        
        // Go over them and start them
        val dispatcher = Akka.system.actorSelection("user/TuktuDispatcher")
        jobs.foreach(job => dispatcher ! new DispatchRequest(job, None, false, false, false, None))
        
        Ok("")
    }}
}