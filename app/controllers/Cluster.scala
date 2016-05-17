package controllers

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.pattern.ask
import akka.util.Timeout
import monitor.AddNode
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api.ClusterNode
import tuktu.utils.util
import play.api.libs.concurrent.Akka
import play.api.Logger
import monitor.DeleteNode
import monitor.DeleteNode

object Cluster extends Controller {
    val clusterParamsMapping = Map(
            "configRepo" -> ("configRepo", "string"),
            "homeAddress" -> ("homeAddress", "string"),
            "logLevel" -> ("logLevel", "string"),
            "timeout" -> ("timeout", "int"),
            "webUrl" -> ("web.url", "string"),
            "jsurl" -> ("web.jsurl", "string"),
            "jsname" -> ("web.jsname", "string"),
            "meta" -> ("tuktu.metarepo", "string"),
            "webRepo" -> ("web.repo", "string"),
            "logDpContent" -> ("mon.log_dp_content", "boolean"),
            "maxHealthFails" -> ("mon.max_health_fails", "int"),
            "healthInterval" -> ("mon.health_interval", "int"),
            "finishExp" -> ("mon.finish_expiration", "long"),
            "errorExp" -> ("mon.error_expiration", "long"),
            "bpBounceMs" -> ("mon.bp.bounce_ms", "int"),
            "bpMaxBounce" -> ("mon.bp.max_bounce", "int"),
            "bpQSize" -> ("mon.bp.blocking_queue_size", "int"),
            "dfsBlockSize" -> ("tuktu.dfs.blocksize", "int"),
            "dfsPrefix" -> ("tuktu.dfs.prefix", "string"),
            "dfsNft" -> ("tuktu.dfs.nft_file", "string"),
            "dbRepl" -> ("tuktu.db.replication", "int")
    )
    
    /**
     * Shows cluster status
     */
    def overview() = Action { implicit request =>
        // Get the cluster settings from cache
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        
        // Fetch all the other parameters
        val params = clusterParamsMapping.map(_ match {
            case (vName, cName) => {
                vName -> Cache.get(cName._1).getOrElse("").toString
            }
        })

        Ok(views.html.cluster.overview(
                util.flashMessagesToMap(request), params, clusterNodes
        ))
    }

    /**
     * Updates specifics (cached values) for the Tuktu cluster
     */
    def updateCluster() = Action { implicit request => {
        // Get the request's values
        val newValues = request.body.asFormUrlEncoded
        newValues match {
            case None => Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            case Some(newVals) => {
                newVals.foreach(value => {
                    // Get the name and the actual value
                    val fieldName = value._1
                    val actualVal = value._2.head
                    
                    // Find the accompanying cache name
                    val cacheVar = clusterParamsMapping.filter(_._1 == fieldName).head
                    cacheVar._2._2 match {
                        case "int" => Cache.set(cacheVar._2._1, actualVal.toInt)
                        case "long" => Cache.set(cacheVar._2._1, actualVal.toLong)
                        case "boolean" => Cache.set(cacheVar._2._1, actualVal.toBoolean)
                        case _ => Cache.set(cacheVar._2._1, actualVal)
                    }
                })
                
                // Redirect back to overview
                Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully updated cluster configuration!"))
            }
        }
    }}
    
    /**
     * Removes a node from the cluster
     */
    def removeNode(address: String) = Action { implicit request =>
        // Get the node and remove it from cache
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())

        clusterNodes.get(address) match {
            case None =>
                // Redirect back to overview
                Redirect(routes.Cluster.overview).flashing("error" -> ("No node at address " + address + " registered in cluster."))
            case Some(node) => {
                clusterNodes -= address
                
                // Notify all other cluster nodes too
                clusterNodes.values.foreach(node => {
                    if (node.host != address) {
                        Akka.system.actorSelection(
                                "akka.tcp://application@" + node.host  + ":" + node.akkaPort + "/user/TuktuHealthChecker"
                        ) ! new DeleteNode(node.host)
                    }
                })

                // Redirect back to overview
                Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully removed node " + address + ":" + node.akkaPort + " from the cluster."))                
            }
        }
    }
    
    /**
     * Allows to add a node to the cluster
     */
    def addNode() = Action { implicit request =>
        Ok(views.html.cluster.addNode(
                util.flashMessagesToMap(request)
        ))
    }

    val addNodeForm = Form(
        mapping(
            "host" -> text.verifying(nonEmpty, minLength(1)),
            "akkaPort" -> number,
            "UIPort" -> number
        ) (ClusterNode.apply)(ClusterNode.unapply)
    )
    def addNodePost() = Action.async { implicit request => {
        implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        
        addNodeForm.bindFromRequest.fold(
            formWithErrors => Future {
                Redirect(routes.Cluster.overview).flashing("error" -> "Address or port number incorrect.")
            },
            node => {
                // Inform the added node about our cluster
                val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                val addFut = Future.sequence(for (cNode <- clusterNodes.values) yield {
                    Akka.system.actorSelection(
                            "akka.tcp://application@" + node.host  + ":" + node.akkaPort + "/user/TuktuHealthChecker"
                    ) ? new AddNode(cNode)
                })
                
                // Only add stuff if we succeeded
                addFut.map(_ => {
                    // Update cache
                    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                    clusterNodes += node.host -> node
                    
                    // Inform the other nodes in our current cluster
                    for (cNode <- clusterNodes.values) yield {
                        Akka.system.actorSelection(
                                "akka.tcp://application@" + cNode.host  + ":" + cNode.akkaPort + "/user/TuktuHealthChecker"
                        ) ? new AddNode(node)
                    }
                    
                    Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully added node " + node.host + ":" + node.akkaPort + " to the cluster."))
                }).recover {
                    case a: Any => {
                        Logger.error("Failed to add node: " + node.host)
                        // Redirect back to overview
                        Redirect(routes.Cluster.overview).flashing("error" -> ("Failed to add node " + node.host + ":" + node.akkaPort + " to the cluster."))
                    }
                }
            }
        )
    }}
}