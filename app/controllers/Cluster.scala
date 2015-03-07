package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import tuktu.utils.util
import play.api.cache.Cache
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form

object Cluster extends Controller {
    /**
     * Shows cluster status
     */
    def overview() = Action { implicit request =>
        // Get the cluster settings from cache
        val configRepo = Cache.getAs[String]("configRepo").getOrElse(null)
        val homeAddress = Cache.getAs[String]("homeAddress").getOrElse(null)
        val logLevel = Cache.getAs[String]("logLevel").getOrElse(null)
        val clusterNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(null)

        Ok(views.html.cluster.overview(
                util.flashMessagesToMap(request),
                configRepo, homeAddress, logLevel, clusterNodes
        ))
    }
    
    /**
     * Removes a node from the cluster
     */
    def removeNode(address: String) = Action { implicit request =>
        // Get the node and remove it from cache
        val clusterNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map[String, String]())
        val port = clusterNodes(address)
        Cache.set("clusterNodes", clusterNodes - address)
        
        // Redirect back to overview
        Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully removed node " + address + ":" + port + " from the cluster."))
    }
    
    /**
     * Allows to add a node to the cluster
     */
    def addNode() = Action { implicit request =>
        Ok(views.html.cluster.addNode(
                util.flashMessagesToMap(request)
        ))
    }
    
    case class addNodeClass(
        address: String, port: Int
    )
    
    val addNodeForm = Form(
        mapping(
            "address" -> text.verifying(nonEmpty, minLength(1)),
            "port" -> number
        ) (addNodeClass.apply)(addNodeClass.unapply)
    )
    def addNodePost() = Action { implicit request => {
        addNodeForm.bindFromRequest.fold(
                formWithErrors => {
                    Redirect(routes.Cluster.addNode).flashing("error" -> "Address or port number incorrect.")
                },
                node => {
                    // Update cache
                    Cache.set("clusterNodes", {
                        Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(Map[String, String]()) +
                            (node.address -> node.port.toString)
                    })
                    
                    // Redirect back to overview
                    Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully added node " + node.address + ":" + node.port + " to the cluster."))
                }
            )
    }}
}