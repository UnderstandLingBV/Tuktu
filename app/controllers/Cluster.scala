package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.cache.Cache
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.Play
import tuktu.api.ClusterNode
import tuktu.utils.util
import scala.collection.JavaConverters.asScalaBufferConverter

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

        Ok(views.html.cluster.overview(
                util.flashMessagesToMap(request),
                configRepo, homeAddress, logLevel, timeout, clusterNodes
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
    def addNodePost() = Action { implicit request => {
        addNodeForm.bindFromRequest.fold(
            formWithErrors => {
                Redirect(routes.Cluster.overview).flashing("error" -> "Address or port number incorrect.")
            },
            node => {
                // Update cache
                val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                clusterNodes += node.host -> node

                // Redirect back to overview
                Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully added node " + node.host + ":" + node.akkaPort + " to the cluster."))
            }
        )
    }}
}