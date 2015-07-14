package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import tuktu.utils.util
import play.api.cache.Cache
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.Form
import play.api.Play
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
        val clusterNodes = Cache.getAs[Map[String, String]]("clusterNodes").getOrElse(null)
        
        // Get the proper ports
        val cNodes = Play.current.configuration.getConfigList("tuktu.cluster.nodes") match {
            case Some(nodeList) => {
                // Get all the nodes in the list and put them in a map
                (for (node <- nodeList.asScala) yield {
                    node.getString("host").getOrElse("") -> node.getString("uiport").getOrElse("")
                }).toMap
            }
            case None => Map[String, String]()
        }
        val uiClusterNodes = clusterNodes.map(elem => {
            elem._1 -> (elem._2, cNodes(elem._1))
        })

        Ok(views.html.cluster.overview(
                util.flashMessagesToMap(request),
                configRepo, homeAddress, logLevel, timeout, uiClusterNodes
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
                Redirect(routes.Cluster.overview).flashing("success" -> ("Successfully uodated cluster configuration!."))
            }
        )
    }}
    
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
                Redirect(routes.Cluster.overview).flashing("error" -> "Address or port number incorrect.")
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