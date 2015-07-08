package controllers.dfs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.dfs.actors.DFSListRequest
import tuktu.dfs.actors.DFSResponse

object Browser  extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    /**
     * Shows the main filebrowser
     */
    def index() = Action {
        Ok(views.html.dfs.browser())
    }
    
    /**
     * Fetches files aasynchronously for a specifc folder
     */
    def getFiles() = Action.async { implicit request =>
        // Get filename
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        val filename = body("filename").head
        
        // Ask the DFS Daemon for the files
        val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new DFSListRequest(filename)).asInstanceOf[Future[DFSResponse]]
        
        fut.map(response => {
            // Check what the response is
            if (response == null)
                Ok(views.html.dfs.files(null))
            else {
                // Check if it is a directory or not
                if (response.isDirectory)
                    Ok(views.html.dfs.files(response.files))
                else Ok("File")
            }
        })
    }
}