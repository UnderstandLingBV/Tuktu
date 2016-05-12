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
    /**
     * Shows cluster status
     */
    def overview() = Action { implicit request =>
        // Get the cluster settings from cache
        val configRepo = Cache.getAs[String]("configRepo").getOrElse(null)
        val homeAddress = Cache.getAs[String]("homeAddress").getOrElse(null)
        val logLevel = Cache.getAs[String]("logLevel").getOrElse(null)
        val timeout = Cache.getAs[Int]("timeout").getOrElse(5)
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        
        // Fetch all the other parameters
        val otherParams = List(
                "webUrl" -> "web.url",
                "jsurl" -> "web.jsurl",
                "jsname" -> "web.jsname"
        ).map(_ match {
            case (vName, cName) => {
                vName -> Cache.get(cName).getOrElse("").toString
            }
        }).toMap

        Ok(views.html.cluster.overview(
                util.flashMessagesToMap(request),
                configRepo, homeAddress, logLevel, timeout, clusterNodes,
                otherParams
        ))
    }
    
    case class updateClusterClass(
        configRepo: String, homeAddress: String, logLevel: String, timeout: Int
    )
    
    val updateClusterForm = Form(
        mapping(
            "configRepo" -> text.verifying(nonEmpty, minLength(1)),
            "homeAddress" -> text.verifying(nonEmpty, minLength(5)),
            "logLevel" -> text.verifying(nonEmpty, minLength(1)),
            "timeout" -> number
        ) (updateClusterClass.apply)(updateClusterClass.unapply)
    )
    /**
     * Updates specifics (cached values) for the Tuktu cluster
     */
    def updateCluster() = Action { implicit request => {
        updateClusterForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Some values were found incorrect.")
            },
            cluster => {
                // Update cache values
                Cache.set("configRepo", cluster.configRepo)
                Cache.set("homeAddress", cluster.homeAddress)
                Cache.set("logLevel", cluster.logLevel)
                Cache.set("timeout", cluster.timeout)
                
                // Redirect back to overview
                Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully updated cluster configuration!"))
            }
        )
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