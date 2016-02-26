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
import play.api.Play
import tuktu.api.ClusterNode
import tuktu.dfs.actors.TDFSOverviewPacket
import tuktu.dfs.actors.TDFSOverviewReply
import java.nio.file.Paths
import tuktu.dfs.actors.TDFSReadInitiateRequest
import akka.actor.ActorLogging
import play.api.libs.iteratee.Enumerator
import akka.actor.Actor
import play.api.libs.iteratee.Concurrent
import tuktu.dfs.actors.TDFSContentPacket
import tuktu.api.StopPacket
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Input
import akka.actor.PoisonPill
import akka.actor.Props
import tuktu.api.InitPacket

object Browser extends Controller {
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
        val isFolder = body("isFolder").head.toBoolean

        // Ask all TDFS daemons for the filename
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        val futs = clusterNodes.map(node => {
            (Akka.system.actorSelection({
                if (node._1 == Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")) "user/tuktu.dfs.Daemon"
                else "akka.tcp://application@" + node._2.host + ":" + node._2.akkaPort + "/user/tuktu.dfs.Daemon"
            }) ? new TDFSOverviewPacket(filename, isFolder)).asInstanceOf[Future[TDFSOverviewReply]]
        })

        // Get all results in
        Future.sequence(futs).map(replies => {
            // Determine index
            val pList = {
                val p = Paths.get(filename)
                util.pathBuilderHelper(p.iterator)
            }

            // Combine replies
            val folders = replies.flatMap(elem => elem.files.filter(_._2.isEmpty).map(_._1)).toList
            val files = replies.flatMap(elem => elem.files.filter(!_._2.isEmpty)).toList.groupBy(_._1).map(file => {
                file._1 -> file._2.map(prt => prt._2).flatten
            })

            Ok(views.html.dfs.files(pList.take(pList.size - 1), folders, files))
        })

    }

    /**
     * Helper actor to serve out files
     */
    class FileServingActor(filename: String) extends Actor with ActorLogging {
        var enum: Enumerator[Array[Byte]] = _
        var channel: Channel[Array[Byte]] = _
        
        def receive() = {
            case ip: InitPacket => {
                Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new TDFSReadInitiateRequest(filename, false, None)
                
                // Set up enumerator and channel
                val res = Concurrent.broadcast[Array[Byte]]
                enum = res._1
                channel = res._2
                // Return them
                sender ! enum
            }
            case tcp: TDFSContentPacket => channel.push(tcp.content)
            case sp: StopPacket => {
                channel.push(Input.EOF)
                self ! PoisonPill
            }
        }
    }

    /**
     * Serves out a file
     */
    def serveFile(filename: String) = Action.async {
        // Set up actor
        val ar = Akka.system.actorOf(Props(classOf[FileServingActor], filename), java.util.UUID.randomUUID.toString)
        val fut = (ar ? new InitPacket).asInstanceOf[Future[Enumerator[Array[Byte]]]]

        fut.map(enum =>
            // Send file to user
            Ok.chunked(enum)
        )
    }
}