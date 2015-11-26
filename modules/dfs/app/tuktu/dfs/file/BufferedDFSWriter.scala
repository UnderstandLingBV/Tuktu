package tuktu.dfs.file

import java.io.BufferedWriter
import java.io.Writer
import akka.actor.ActorRef
import play.api.cache.Cache
import akka.actor.Identify
import akka.actor.ActorIdentity
import scala.concurrent.Await
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.ClusterNode
import tuktu.api.DFSOpenRequest
import tuktu.api.DFSWriteRequest
import tuktu.api.DFSCloseRequest
import java.io.FileOutputStream

/**
 * Wrapper around BufferedWriter that uses the DFS Daemons of other nodes to write the same content
 * to all files in parallel
 */
class BufferedDFSWriter(writer: Writer, filename: String, encoding: String, otherNodes: List[String]) extends BufferedWriter(writer) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
    
    // Get the remote DFS actors once
    val remoteDfsActors = {
        val futures = for (hostname <- otherNodes) yield {
            val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname).akkaPort + "/user/tuktu.dfs.Daemon"
            // Get the identity
            (Akka.system.actorSelection(location) ? Identify(None)).asInstanceOf[Future[ActorIdentity]]
        }
        Await.result(Future.sequence(futures), timeout.duration).map(id => id.getRef)
    }
    
    // Open the files on other locations too
    remoteDfsActors.foreach(actor => {
        actor ! new DFSOpenRequest(filename, encoding)
    })
    
    /**
     * Writes a string to a file
     */
    override def write(content: String) = {
        // Stream the data to the other nodes too
        super.write(content)
        
        remoteDfsActors.foreach(actor => {
            actor ! new DFSWriteRequest(filename, content)
        })
    }
    
    /**
     * Closes the file
     */
    override def close() = {
        super.close
        
        remoteDfsActors.foreach(actor => {
            actor ! new DFSCloseRequest(filename)
        })
    }
}

class PartitionedDFSWriter(filename: String, filepart: Int, blockSize: Int, otherNodes: List[String]) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
    
    // Get the remote DFS actors once
    val remoteDfsActors = {
        val futures = for (hostname <- otherNodes) yield {
            val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname).akkaPort + "/user/tuktu.dfs.Daemon"
            // Get the identity
            (Akka.system.actorSelection(location) ? Identify(None)).asInstanceOf[Future[ActorIdentity]]
        }
        Await.result(Future.sequence(futures), timeout.duration).map(id => id.getRef)
    }
    
    // Open the files on other locations too
   /* remoteDfsActors.foreach(actor => {
        actor ! new DFSOpenRequest(filename, encoding)
    })*/
    
    // Keep track of bytes processed
    var byteCount = 0
    // Open the file output stream
    var writer = new FileOutputStream(filename + "-part" + filepart)
    
    def write(content: String, charset: String = null): Unit = charset match {
        case null => write(content.getBytes)
        case cs => write(content.getBytes(cs))
    }
    
    def write(content: Array[Byte]): Unit = {
        // Write bytes
        if (byteCount + content.length > blockSize) {
            // Write the part we need to and rotate
            writer.write(content, 0, blockSize - byteCount)
            // Get remainder
            val remainder = content.slice(blockSize - byteCount, content.length)
            // @TODO: Send to the next block writer, but check if we are so we dont have network latency
        }
        else {
            // Write entirely
            writer.write(content)
            byteCount += content.length
        }
    }
}