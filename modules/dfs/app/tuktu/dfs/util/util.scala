package tuktu.dfs.util

import play.api.cache.Cache
import play.api.Play.current
import tuktu.dfs.actors.DFSDaemon
import scala.util.hashing.MurmurHash3
import tuktu.dfs.actors.DFSDaemon

object util {
    /**
     * Hashes an idnex to a (number of) node(s)
     */
    def indexToNodeHasher(keyString: String, replicationCount: Option[Int]): List[String] = {
        def indexToNodeHasherHelper(nodes: List[String], replCount: Int): List[String] = {
            // Get a node
            val node = nodes(Math.abs(MurmurHash3.stringHash(keyString) % nodes.size))
            
            // Return more if required
            node::{if (replCount > 1) {
                indexToNodeHasherHelper(nodes diff List(node), replCount - 1)
            } else List()}
        }
        
        val clusterNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map[String, String]()).keys.toList diff
            List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
        replicationCount match {
            case Some(cnt) => indexToNodeHasherHelper(clusterNodes, cnt - 1)
            case None => clusterNodes
        }
    }
    
    /**
     * Gets the DFS index for a filename
     */
    def getIndex(filename: String) = {
        // See what to split on
        val index = {
            if (filename.contains("\\")) filename.split("\\\\")
            else filename.split("/")
        } toList
        
        (index, index.dropRight(1))
    }
}