package tuktu.dfs.actors

import akka.actor.ActorLogging
import akka.actor.Actor
import java.io.File
import play.api.cache.Cache
import play.api.Play
import play.api.Play.current
import java.io.IOException
import tuktu.api._
import scala.util.hashing.MurmurHash3
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import tuktu.dfs.util.util.getIndex

case class DFSListRequest(
        filename: String
)
case class DFSResponse(
        files: List[String],
        isDirectory: Boolean
)
case class DFSObject(
        filename: String,
        isDirectory: Boolean
)

/**
 * Central point of communication for the DFS
 */
class DFSDaemon extends Actor with ActorLogging {
    // File map to keep in-memory. A map from a list of (sub)directories to a hashset containing the files
    val dfsTable = collection.mutable.Map[List[String], collection.mutable.HashSet[String]]()
    // Get the prefix
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")
    
    // Open files
    val openFiles = collection.mutable.Map[String, BufferedWriter]()
    
    /**
     * Resolves a file, gives a DFSReply
     */
    private def resolveFile(filename: String): Option[File] = {
        // Get indexes
        val (index, dirIndex) = getIndex(filename)
        
        // See if it exists
        if (!dfsTable.contains(index) && !dfsTable.contains(dirIndex)) None
        else Some(new File(prefix + "/" + filename))
    }
    
    /**
     * Creates a directory on disk and adds it to the DFS
     */
    private def makeDir(index: List[String], filename: String) = {
        val dirName = prefix + "/" + filename
        if (!dfsTable.contains(index)) {
            val file = new File(dirName)
            val res = if (!file.exists) file.mkdirs else true
                
            // Add to our DFS Table
            if (res)
                dfsTable += index -> collection.mutable.HashSet[String]()
            
            // Return success or not
            res
        }
        else true // Already existed
    }
    
    /**
     * Creates a file or directory and adds it to the DFS
     */
    private def createFile(filename: String, isDirectory: Boolean): Option[File] = {
        // Get indexes
        val (index, dirIndex) = getIndex(filename)
        
        // See if we need to make a directory or a file
        if (isDirectory) {
            val success = makeDir(index, filename)
            if (success) Some(new File(prefix + "/" + filename))
            else None
        }
        else {
            // It's a file, first see if we need to/can make a dir
            val dirSuccess = makeDir(dirIndex, filename)
            if (!dirSuccess) None
            else {
                // Make the file
                val res = try {
                    // Make file
                    val file = new File(prefix + "/" + filename)
                    file.createNewFile
                    
                    // Add to map
                    dfsTable(dirIndex) += index.takeRight(1).head
                    
                    Some(file)
                } catch {
                    case e: IOException => {
                        log.warning("Failed to create DFS file " + filename)
                        None
                    }
                }
                
                res
            }
        }
    }
    
    /**
     * Recursively deletes directories and files in it
     */
    private def deleteDirectory(directory: File): Boolean = {
        // Get all files in this directory
        val files = directory.listFiles
        (for (file <- files) yield {
            // Recurse or not?
            if (file.isDirectory) deleteDirectory(file)
            else file.delete
        }).toList.foldLeft(true)(_ && _)
    }
    
    /**
     * Deletes a file or directory
     */
    private def deleteFile(filename: String, isDirectory: Boolean) = {
        // Get indexes
        val (index, dirIndex) = getIndex(filename)
        
        // See if it's a directory or a file
        if (isDirectory) {
            // Delete directory from our DFS Table and from disk
            val res = deleteDirectory(new File(prefix + "/" + filename))
            dfsTable -= index
            
            res
        }
        else {
            // Remove the file from disk and DFS
            val res = new File(prefix + "/" + filename).delete
            if (dfsTable(dirIndex).size == 1) dfsTable -= dirIndex
            else dfsTable(dirIndex) -= index.takeRight(1).head
            
            res
        }
    }
    
    /**
     * On initialization, add all the current files to the in memory table store
     */
    private def initialize(directory: File): Unit = {     
        val (index, dirIndex) = getIndex(directory.toString)
        // initialize this directory
        dfsTable += index -> collection.mutable.HashSet[String]()
        // add files or add another directory
        directory.listFiles.foreach { file => {
                if(file.isDirectory) 
                    initialize(file)
                else
                    dfsTable(index) += file.toString
            }
        }        
    }
    
    def receive() = {
        case rr: DFSReadRequest => {
            // Fetch the file and send back
            sender ! new DFSReadReply(resolveFile(rr.filename))
        }
        case cr: DFSCreateRequest => {
            // Create file and send back
            sender ! new DFSCreateReply(createFile(cr.filename, cr.isDirectory))
        }
        case dr: DFSDeleteRequest => {
            // Delete the file and send back
            sender ! new DFSDeleteReply(deleteFile(dr.filename, dr.isDirectory))
        }
        case or: DFSOpenRequest => {
            val filename = prefix + "/" + or.filename
            // Create first if needed
            if (!openFiles.contains(filename)) createFile(or.filename, false)
            
            if (!openFiles.contains(filename)) {
                // Open file
                val file = new File(filename)
                // Add to our list
                openFiles += filename -> new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), or.encoding))
            }
        }
        case cr: DFSCloseRequest => {
            val filename = prefix + "/" + cr.filename
            if (openFiles.contains(filename)) {
                // Close writer
                openFiles(filename).flush
                openFiles(filename).close
                // Remove from map
                openFiles -= filename
            }
        }
        case wr: DFSWriteRequest => {
            val filename = prefix + "/" + wr.filename
            // Write to our file
            if (openFiles.contains(filename))
                openFiles(filename).write(wr.content)
        }
        case init: InitPacket => {
            // Check if directory exists, if not, make it
            val rootFolder = new File(prefix)
            if (!rootFolder.exists)
                rootFolder.mkdirs
            
            initialize(rootFolder)
        }
        case lr: DFSListRequest => {
            // Send back the files in this folder, if any
            val filename = prefix + "/" + lr.filename
            val (index, dirIndex) = getIndex(filename)
            
            // Check if this is a directory or a file
            val response = {
                if (dfsTable.contains(index))
                    // It's a directory
                    new DFSResponse(dfsTable(index).toList.map(elem => elem.drop(prefix.size + 1)), true)
                else {
                    if (dfsTable.contains(dirIndex))
                        // It's a file
                        new DFSResponse(List(lr.filename.drop(prefix.size + 1)), false)
                    else
                        // Not found
                        null
                }
            }
            
            // Send back response
            sender ! response
        }
        case _ => {}
    }
}