package tuktu.dfs.util

import play.api.cache.Cache
import play.api.Play.current
import scala.util.hashing.MurmurHash3

object util {
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