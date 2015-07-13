package tuktu.api

import java.io.BufferedReader
import java.io.File
import java.io.Reader

import scala.io.Codec

import akka.actor.ActorSelection.toScala
import play.api.Play.current
import play.api.libs.concurrent.Akka

// Requests
case class DFSReadRequest(
        filename: String
)
case class DFSCreateRequest(
        filename: String,
        isDirectory: Boolean
)
case class DFSDeleteRequest(
        filename: String,
        isDirectory: Boolean
)
case class DFSOpenRequest(
        filename: String,
        encoding: String
)
case class DFSOpenReadRequest(
        filename: String
)
case class DFSLocalOpenRequest(
        filename: String
)
case class DFSWriteRequest(
        filename: String,
        content: String
)
case class DFSCloseRequest(
        filename: String
)
case class DFSLocalCloseRequest(
        filename: String
)
case class DFSCloseReadRequest(
        filename: String
)
case class DFSListRequest(
        filename: String
)
case class DFSOpenFileListRequest()

// Replies
case class DFSReadReply(
        files: Option[File]
)
case class DFSCreateReply(
        file: Option[File]
)
case class DFSDeleteReply(
        success: Boolean
)
case class DFSResponse(
        files: Map[String, DFSElement],
        isDirectory: Boolean
)
case class DFSOpenFileListResponse(
        localFiles: List[String],
        remoteFiles: List[String],
        readFiles: List[String]
)

case class DFSElement(isDirectory: Boolean)

class BufferedDFSReader(reader: Reader, filename: String)(implicit codec: Codec) extends BufferedReader(reader) {
    // Notify DFS
    Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new DFSOpenReadRequest(filename)
    
    override def close() = {
        // Notify DFS
        Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new DFSCloseReadRequest(filename)
        super.close
    }
}