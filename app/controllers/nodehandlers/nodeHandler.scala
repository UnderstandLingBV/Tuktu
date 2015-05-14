package controllers.nodehandlers

import play.api.cache.Cache
import play.api.Play.current
import org.apache.commons.lang3.StringUtils
import scala.util.Random

/**
 * Common functionality to deal with multiple nodes, returns all nodes to run on
 */
object nodeHandler {
    def handleNodesString(nodes: String): List[String] = {
        // See what node handler we need to use
        nodes match {
            case "" => List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
            case n: String if n == "AllNodes" => {
                // Pass to all nodes on the cluster
                Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map("127.0.0.1" -> "2552")).keys.toList
            }
            case n: String if n.startsWith("SomeNodes ") => {
                // Some nodes can either contain a number that specifies how many to use, or a list of IPs
                val nodeString = n.drop("SomeNodes ".length)
                if (StringUtils.isNumeric(nodeString)) {
                    // Arbitrarily pick some nodes
                    val allNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map("127.0.0.1" -> "2552")).keys.toList
                    Random.shuffle(allNodes).take(nodeString.toInt)
                }
                else nodeString.split(",").map(node => node.trim).toList
            }
            case n: String if n.startsWith("SingleNode ") => {
                // Single node address, get it
                List(n.drop("SingleNode ".length))
            }
        }
    }
}