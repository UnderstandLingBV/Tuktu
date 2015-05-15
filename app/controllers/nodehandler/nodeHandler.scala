package controllers.nodehandler

import play.api.cache.Cache
import play.api.Play.current
import org.apache.commons.lang3.StringUtils
import scala.util.Random
import play.api.libs.json.JsObject

/**
 * Common functionality to deal with multiple nodes, returns all nodes to run on
 */
object nodeHandler {
    def handleNodes(nodes: List[JsObject]): List[(String, Int)] = nodes match {
        case Nil => {
            // No nodes given, use localhost as default
            List((Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"), 1))
        }
        case _ => {
            // Iterate over node configurations
            (for {
                node <- nodes
                
                // Get the handler type
                handlerType = (node \ "type").as[String]
                // Get the node list
                nodeList = (node \ "nodes").as[String]
                // Get the amount of instances
                instances = (node \ "instances").asOpt[Int].getOrElse(1)
                
                if (handlerType != "" && nodeList != "")
            } yield {
                // See what node handler we need to use
                handlerType match {
                    case "AllNodes" => {
                        // Pass to all nodes on the cluster
                        val allNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map("127.0.0.1" -> "2552")).keys.toList
                        allNodes.zip((1 to allNodes.size - 1).map(_ => instances))
                    }
                    case "SomeNodes" => {
                        // Some nodes can either contain a number that specifies how many to use, or a list of IPs
                        if (StringUtils.isNumeric(nodeList)) {
                            // Arbitrarily pick some nodes
                            val allNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map("127.0.0.1" -> "2552")).keys.toList
                            val someNodes = Random.shuffle(allNodes).take(nodeList.toInt)
                            someNodes.zip((1 to someNodes.size - 1).map(_ => instances))
                        }
                        else {
                            // Get the nodes we need
                            val someNodes = nodeList.split(",").map(node => node.trim).toList
                            someNodes.zip((1 to someNodes.size - 1).map(_ => instances))
                        }
                    }
                    case _ => {
                        // Single node address, get it
                        List(
                                (nodeList, instances)
                        )
                    }
                }
            }).flatten.toMap.toList
        }
    }
}