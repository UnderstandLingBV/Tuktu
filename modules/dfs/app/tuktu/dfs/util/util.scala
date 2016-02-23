package tuktu.dfs.util

import java.nio.file.Path

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
    
    /**
     * Gets separate folders of a path
     */
    def pathBuilderHelper(iter: java.util.Iterator[Path]): List[String] = if (!iter.hasNext) List.empty[String]
        else iter.next match {
            case null => List.empty[String]
            case p: Path => p.toString::pathBuilderHelper(iter)
        }
}