package monitor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.ClusterNode
import tuktu.api.HealthCheck
import tuktu.api.HealthReply
import play.api.Play
import scala.util.{ Try, Success, Failure }

case class HealthRound()
case class AddNode(
    node: ClusterNode)
case class DeleteNode(
    node: String)

/**
 * Checks the health of the cluster; pings all dispatchers every now and then to see if they
 * are still alive
 */
class HealthMonitor() extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Keep track of how many times we sent a failed message to each node
    val healthChecks = collection.mutable.Map.empty[String, Int].withDefault { _ => 0 }

    // Get maximum number of failures
    val maxFails = Cache.getAs[Int]("mon.max_health_fails").getOrElse(Play.current.configuration.getInt("tuktu.monitor.max_health_fails").getOrElse(3))

    // Set up periodic health checks
    val interval = Cache.getAs[Int]("mon.health_interval").getOrElse(Play.current.configuration.getInt("tuktu.monitor.health_interval").getOrElse(30))
    Akka.system.scheduler.schedule(
        interval seconds,
        interval seconds,
        self,
        new HealthRound)

    def receive = {
        case hr: HealthRound =>
            // Get all other nodes
            val cNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
            val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
            val clusterNodes = cNodes - homeAddress

            // Send out health checks to all nodes
            for ((hostname, node) <- clusterNodes) {
                val location = "akka.tcp://application@" + hostname + ":" + node.akkaPort + "/user/TuktuDispatcher"
                (Akka.system.actorSelection(location) ? new HealthCheck).onComplete {
                    case Success(_) =>
                        // Can remove for now until next failure
                        healthChecks -= hostname
                    case Failure(_) =>
                        // Increase counter and see if we need to remove
                        healthChecks(hostname) += 1
                        if (healthChecks(hostname) >= maxFails) {
                            // Remove from cluster and from health checks
                            cNodes -= hostname
                            healthChecks -= hostname
                        }
                }
            }
        case AddNode(node) =>
            Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()) +=
                node.host -> node

            sender ! "ok"
        case DeleteNode(node) =>
            Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()) -= node
    }
}