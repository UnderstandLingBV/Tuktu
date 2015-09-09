package globals

import tuktu.api.TuktuGlobal
import play.api.Application
import play.api.Play
import java.io.File
import tuktu.api.DispatchRequest
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.pattern.ask
import scala.concurrent.Future
import akka.actor.ActorRef
import play.api.cache.Cache
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Loops through the web analytics configs and boots up instances of the flows present there
 */
class WebGlobal() extends TuktuGlobal() {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // Initialize host map
    Cache.set("web.hostmap", Map[String, ActorRef]())
    
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Get the web analytics configs
        val webRepo = Play.current.configuration.getString("tuktu.webrepo")
            .getOrElse("configs/analytics")
        val hostFolders = new File(webRepo).listFiles
        // These should all be folders that in turn contain a Tuktu.js file in them
        hostFolders.foreach(fldr => {
            val tuktuJsName = fldr.getAbsolutePath + "/Tuktu.json"
            // Sanity checks
            if (fldr.isDirectory) {
                // See if the Tuktu.js file is there
                try {
                    val tuktuJsFile = new File(tuktuJsName)
                    if (tuktuJsFile.exists) {
                        // Boot up the generator
                        val generator = Akka.system.actorSelection("user/TuktuDispatcher") ?
                            new DispatchRequest(
                                    webRepo.drop(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs").size) + "/" + fldr.getName + "/Tuktu",
                                    None, false, true, true, None)
                        generator.asInstanceOf[Future[ActorRef]].onSuccess {
                            case ar: ActorRef => {
                                // Add the actor ref to cache for this host
                                Cache.set("web.hostmap",
                                        Cache.getAs[Map[String, ActorRef]]("web.hostmap").getOrElse(Map[String, ActorRef]()) +
                                        (fldr.getName -> ar))
                            }
                        }
                    }
                } catch {
                    case e: Exception => {}
                }
            }
        })
    }
}