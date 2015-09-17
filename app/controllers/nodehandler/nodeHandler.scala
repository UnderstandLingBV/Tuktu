package controllers.nodehandler

import play.api.cache.Cache
import play.api.Play.current
import org.apache.commons.lang3.StringUtils
import scala.util.Random
import play.api.libs.json.JsObject
import tuktu.api.ClusterNode

/**
 * Common functionality to deal with multiple nodes, returns all nodes to run on
 */
object nodeHandler {
    def handleNodes(nodes: List[JsObject]): List[(String, Int)] = nodes match {
        case Nil => {
            // No nodes given, use localhost as default
            List((Cache.getOrElse[String]("homeAddress")("127.0.0.1"), 1))
        }
        case _ => {
            // Get all nodes from Cache
            val allNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()).keys

            // Iterate over node configurations
            (for {
                node <- nodes

                // Get the handler type
                handlerType = (node \ "type").as[String]
                // Get the node list
                nodeList = (node \ "nodes").as[String]
                // Get the amount of instances
                instances = (node \ "instances").asOpt[Int].getOrElse(1)

                if (handlerType != "" && (nodeList != "" || handlerType == "AllNodes"))
            } yield {
                // See what node handler we need to use
                handlerType match {
                    case "AllNodes" => {
                        // Pass to all nodes on the cluster
                        allNodes.map(node => (node, instances))
                    }
                    case "SomeNodes" => {
                        // Some nodes can either contain a number that specifies how many to use, or a list of IPs
                        if (StringUtils.isNumeric(nodeList)) {
                            // Arbitrarily pick some nodes
                            Random.shuffle(allNodes).take(nodeList.toInt).map(node => (node, instances))
                        } else {
                            // Get the nodes we need
                            nodeList.split(',').toList.map(node => (node.trim, instances))
                        }
                    }
                    case _ => {
                        // Single node address, get it
                        List((nodeList, instances))
                    }
                }
            }).flatten.toMap.toList
        }
    }
}