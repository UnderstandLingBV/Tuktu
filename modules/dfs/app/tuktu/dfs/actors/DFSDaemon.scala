package tuktu.dfs.actors

import akka.actor.ActorLogging
import akka.actor.Actor
import java.io.File
import play.api.cache.Cache
import play.api.Play
import play.api.Play.current

case class ReadRequest(
        filename: String,
        bufferSize: Int
)
case class ProbeRequest(
        filename: String
)

/**
 * Central point of communication for the DFS
 */
class DFSDaemon extends Actor with ActorLogging {
    /**
     * Recursively builds a file table containing the files and folders present on the DFS
     */
    def buildFileTable(directory: File): Unit = {
        // Get files from directory
        val files = directory.listFiles
        
        // Add all files and folder
        files.map(file => {
            if (!file.isDirectory)
                // Just a file, add it
                Cache.set(file.getPath, file)
            else {
                // Recurse
                Cache.set(file.getPath, file)
                buildFileTable(file)
            }
        })
    }
    
    /**
     * Tries to locate a file on the DFS and returns it
     */
    def fetchFile(filename: String) = {
        
    }
    
    // Initial setup, build file table
    buildFileTable(new File(Play.current.configuration.getString("tuktu.dfs.root").getOrElse("dfs")))
    
    def receive() = {
        case pr: ProbeRequest => {
            // Check if the file exists
            Cache.get(pr.filename) match {
                case None => sender ! false
                case _ => sender ! true
            }
        }
        case rr: ReadRequest => {
            // Fetch the file
        }
    }
}