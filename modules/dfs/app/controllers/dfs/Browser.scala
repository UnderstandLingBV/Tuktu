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
import tuktu.api.DFSElement
import tuktu.api.DFSListRequest
import tuktu.api.DFSResponse
import tuktu.dfs.util.util
import tuktu.api.DFSOpenFileListResponse
import tuktu.api.DFSOpenFileListRequest

object Browser  extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    /**
     * Shows the main filebrowser
     */
    def index() = Action {
        Ok(views.html.dfs.browser())
    }
    
    /**
     * Fetches files asynchronously for a specific folder
     */
    def getFiles() = Action.async { implicit request =>
        // Get filename
        val body = request.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
        val filename = body("filename").head
        val index = util.getIndex(filename)._1.filter(!_.isEmpty)
        
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
    
    /**
     * Fetches open files asynchronously
     */
    def getOpenFiles() = Action.async { implicit request =>
        // Ask the DFS Daemon for the files
        val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new DFSOpenFileListRequest()).asInstanceOf[Future[DFSOpenFileListResponse]]
        fut.map(resp => {
            Ok(views.html.dfs.listOpenFiles(resp.localFiles, resp.remoteFiles, resp.readFiles))
        })
    }
}