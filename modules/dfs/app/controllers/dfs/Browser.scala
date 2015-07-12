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
import tuktu.dfs.util._
import tuktu.dfs.actors.DFSElement

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
        val index = util.getIndex(filename)._1
        
        // Ask the DFS Daemon for the files
        val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new DFSListRequest(filename)).asInstanceOf[Future[Option[DFSResponse]]]
        
        fut.map(resp => {
            // Check what the response is
            resp match {
                case None => Ok(views.html.dfs.files(index, null, null))
                case Some(response) => {
                    // Check if it is a directory or not
                    if (response.isDirectory) {
                        // We should list the files and folders
                        val folders = response.files.collect {
                                case el: (String, DFSElement) if el._2.isDirectory => el._1
                        }
                        val files = response.files.collect {
                                case el: (String, DFSElement) if !el._2.isDirectory => el._1
                        }
                        
                        Ok(views.html.dfs.files(index, folders toList, files toList))
                    }
                    else Ok("File")
                }
            }
        })
    }
}