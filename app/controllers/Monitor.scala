package controllers

import java.io.File
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

object Monitor extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Fetches the monitor's info
     */
    def fetchLocalInfo() = Action.async { implicit request =>
        // Get the monitor
        val fut = (Akka.system.actorSelection("user/TuktuMonitor") ? new MonitorOverviewPacket()).asInstanceOf[Future[Map[String, AppMonitorObject]]]
        fut.map(res =>
            Ok(views.html.monitor.showApps(
                    res.toList.sortBy(elem => elem._2.getStartTime),
                    util.flashMessagesToMap(request)
            ))
        )
    }
    
    /**
     * Terminates a Tuktu job
     */
    def terminate(name: String, force: Boolean) = Action {
        // Send stop packet to actor
        if (force) {
            Akka.system.actorSelection(name) ! PoisonPill
            // Must inform the monitor since the generator won't do it itself on poisonpill
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    name,
                    System.currentTimeMillis / 1000L,
                    "done"
            )
        }
        else
            Akka.system.actorSelection(name) ! new StopPacket
        
        Redirect(routes.Monitor.fetchLocalInfo()).flashing("success" -> ("Successfully " + {
            force match {
                case true => "terminated"
                case _ => "stopped"
            }
        } + " job " + name))
    }
    
    /**
     * Shows the start-job view
     */
    def startJobView() = Action { implicit request => {
            // Get the configs folder
            val configRepo = Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")
            // Get configs
            val configs = new File(configRepo).listFiles.map(cfg => cfg.getName.take(cfg.getName.size - 5))
            
            Ok(views.html.monitor.startJob(
                    configs,
                    util.flashMessagesToMap(request)
            ))
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
                    Redirect(routes.Monitor.startJobView).flashing("error" -> "Invalid job name or instances")
                },
                job => {
                    Akka.system.actorSelection("user/TuktuDispatcher") ! new DispatchRequest(job.name, None, false, false, false, None)
                    Redirect(routes.Monitor.fetchLocalInfo).flashing("success" -> ("Successfully started job " + job.name))
                }
            )
        }
    }
}