package controllers.restapi

import play.api.mvc.Action
import play.api.mvc.Controller
import java.io.File
import play.api.libs.iteratee.Iteratee
import play.api.mvc.BodyParser
import play.api.mvc.RawBuffer
import akka.actor.ActorRef
import tuktu.dfs.actors.TDFSContentPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import scala.concurrent.Await
import tuktu.dfs.actors.TDFSWriteInitiateRequest
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.pattern.ask
import play.api.cache.Cache
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import tuktu.api.StopPacket

/**
 * DFS file handling from API
 */
object DFS extends Controller {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Custom body parser for adding a file to DFS
     */
    def parseDfs(filename: String, binary: Boolean): BodyParser[Unit] = BodyParser("dfs") { request =>
        Iteratee.fold[Array[Byte], ActorRef](
                // Set up the writer
                Await.result(Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteInitiateRequest(
                    filename, None, binary, None
                ), timeout.duration).asInstanceOf[ActorRef]
        ){(writer, bytes) =>
            writer ! new TDFSContentPacket(bytes)
            writer
        }.map {writer => {
            writer ! new StopPacket()
            Right(Unit)
        }}
    }

    /**
     * Adds a file to DFS
     */
    def add(name: String, binary: Boolean) = Action(parseDfs(name, binary)) { request =>
        Ok("")
    }

    /**
     * Serves out a TDFS file
     */
    def get(name: String) = controllers.dfs.Browser.serveFile(name)
}