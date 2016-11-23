package globals

import java.nio.file.{ Files, Path, Paths }

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Failure

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.Application
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.{ ClusterNode, DispatchRequest, TuktuGlobal }

/**
 * Loops through the web analytics configs and boots up instances of the flows present there
 */
class WebGlobal() extends TuktuGlobal() {
    implicit val timeout = Timeout(5 seconds)
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
    // Initialize host map
    Cache.set("web.hostmap", collection.mutable.Map[String, ActorRef]())

    // Set other config directives
    Cache.set("web.url", current.configuration.getString("tuktu.url").getOrElse("http://localhost:9000"))
    Cache.set("web.jsname", current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field"))
    Cache.set("web.repo", current.configuration.getString("tuktu.webrepo").getOrElse("configs/analytics"))
    Cache.set("web.set_cookies", current.configuration.getBoolean("tuktu.web.set_id_cookies").getOrElse(true))
}