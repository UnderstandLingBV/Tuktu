package controllers.nodehandler

import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.JsObject
import scala.util.{ Try, Success, Failure, Random }
import tuktu.api.ClusterNode

/**
 * Common functionality to deal with multiple nodes, returns all nodes to run on
 */
object nodeHandler {

    def handleNodes(nodes: List[JsObject]): List[(String, Int)] = {

        val homeAddress = Cache.getOrElse[String]("homeAddress")("127.0.0.1")

        if (nodes.isEmpty) {
            // No nodes given, use localhost as default
            List((homeAddress, 1))
        } else {
            // Get all nodes from Cache
            val allNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()).keys

            // Iterate over node configurations
            nodes.flatMap { node =>

                // Get the handler type
                val handlerType = (node \ "type").as[String]
                // Get the node list; default to home address if empty
                val nodeList = (node \ "nodes").asOpt[String] match {
                    case None | Some("") => homeAddress
                    case Some(s)         => s
                }
                // Get the amount of instances
                val instances = (node \ "instances").asOpt[Int].getOrElse(1)

                handlerType match {
                    case "AllNodes" =>
                        // Pass to all nodes on the cluster
                        allNodes.map { node => (node, instances) }

                    case "SomeNodes" =>
                        // Some nodes can either contain a number that specifies how many to use, or a list of IPs
                        Try { nodeList.trim.toInt } match {
                            case Success(count) =>
                                Random.shuffle(allNodes).take(nodeList.toInt).map { node => (node, instances) }
                            case Failure(_) =>
                                nodeList.split(',').toList.map { node => (node.trim, instances) }
                        }

                    case _ =>
                        // Single node address, get it
                        List((nodeList, instances))
                }
            }.toMap.toList
        }
    }
}