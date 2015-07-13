package tuktu.dfs.file

import java.io.BufferedReader
import java.io.Reader

import scala.io.Codec

import akka.actor.ActorSelection.toScala
import play.api.Play.current
import play.api.libs.concurrent.Akka
import tuktu.api.DFSCloseReadRequest
import tuktu.api.DFSOpenReadRequest

class BufferedDFSReader(reader: Reader, filename: String)(implicit codec: Codec) extends BufferedReader(reader) {
    // Notify DFS
    Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new DFSOpenReadRequest(filename)
    
    override def close() = {
        // Notify DFS
        Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new DFSCloseReadRequest(filename)
        super.close
    }
}