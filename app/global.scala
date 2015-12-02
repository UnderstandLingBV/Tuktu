import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.reflections.Reflections
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.routing.Broadcast
import akka.routing.SmallestMailboxPool
import akka.util.Timeout
import controllers.AutoStart
import controllers.Dispatcher
import controllers.FlowManagerActor
import controllers.TuktuScheduler
import monitor.DataMonitor
import monitor.HealthMonitor
import play.api.Application
import play.api.GlobalSettings
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.NotFound
import tuktu.api.ClusterNode
import tuktu.api.TuktuGlobal
import play.api.Logger

object Global extends GlobalSettings {
    implicit val timeout = Timeout(5 seconds)

    /**
     * Load module globals
     */
    private val moduleGlobals = collection.mutable.ListBuffer[TuktuGlobal]()
    private def LoadModuleGlobals(app: Application) = {
        // Fetch all globals
        val reflections = new Reflections("globals")
        val moduleGlobalClasses = reflections.getSubTypesOf(classOf[TuktuGlobal]).asScala

        for (moduleGlobal <- moduleGlobalClasses) {
            try {
                moduleGlobals += moduleGlobal.newInstance.asInstanceOf[TuktuGlobal]
            } catch {
                case e: Throwable => {
                    Logger.error("Failed loading Global of " + moduleGlobal.getName, e)
                }
            }
        }
    }

    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        // Set timeout
        Cache.set("timeout", Play.current.configuration.getInt("tuktu.timeout").getOrElse(5))
        // Set location where config files are and store in cache
        Cache.set("configRepo", Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs"))
        // Set location of this node
        Cache.set("homeAddress", Play.current.configuration.getString("akka.remote.netty.tcp.hostname").getOrElse("127.0.0.1"))
        // Set log level
        Cache.set("logLevel", Play.current.configuration.getString("tuktu.monitor.level").getOrElse("all"))
        // Get the cluster setup, which nodes are present
        Cache.set("clusterNodes", {
            val clusterNodes = scala.collection.mutable.Map[String, ClusterNode]()
            for (node <- Play.current.configuration.getConfigList("tuktu.cluster.nodes").map(_.asScala).getOrElse(Nil)) {
                val host = node.getString("host").getOrElse("127.0.0.1")
                val akkaPort = node.getString("port").getOrElse("2552").toInt
                val UIPort = node.getString("uiport").getOrElse("9000").toInt
                clusterNodes += host -> new ClusterNode(host, akkaPort, UIPort)
            }
            clusterNodes
        })
        // Routee to Router mapping
        Cache.set("router.mapping", scala.collection.mutable.Map[ActorRef, ActorRef]())

        // Set up monitoring actor
        val monActor = Akka.system.actorOf(Props[DataMonitor], name = "TuktuMonitor")
        monActor ! "init"

        // Set up dispatcher(s), read from config how many
        val dispActor = Akka.system.actorOf(
            SmallestMailboxPool(Play.current.configuration.getInt("tuktu.dispatchers").getOrElse(5))
                .props(Props(classOf[Dispatcher], monActor)), name = "TuktuDispatcher")
        dispActor ! "init"

        // Set up scheduling actor
        val schedActor = Akka.system.actorOf(Props(classOf[TuktuScheduler], dispActor), name = "TuktuScheduler")
        schedActor ! "init"

        // Set up health checker
        val healthChecker = Akka.system.actorOf(Props(classOf[HealthMonitor]), name = "TuktuHealthChecker")
        healthChecker ! "init"

        // Set up FlowManager
        val flowManagerActor = Akka.system.actorOf(Props(classOf[FlowManagerActor], dispActor), name = "FlowManager")
        flowManagerActor ! "init"

        // Load module globals
        LoadModuleGlobals(app)
        for (moduleGlobal <- moduleGlobals) moduleGlobal.onStart(app)

        // Already start running jobs as defined in the autostart file
        AutoStart
    }

    /**
     * Overwrite internal server error page (code 500)
     */
    override def onError(request: RequestHeader, ex: Throwable) = {
        Future.successful(InternalServerError(
            Json.obj("error" -> "Internal server error")))
    }

    /**
     * Overwrite not found error page (code 404)
     */
    override def onHandlerNotFound(request: RequestHeader) = {
        Future.successful(NotFound(
            Json.obj("error" -> "API endpoint not found")))
    }

    /**
     * Overwrite bad request error page (route found, but no binding)
     */
    override def onBadRequest(request: RequestHeader, error: String) = {
        Future.successful(BadRequest(
            Json.obj("error" -> "API endpoint not found")))
    }

    override def onStop(app: Application) {
        // Terminate our dispatchers and monitor
        Akka.system.actorSelection("user/TuktuMonitor") ! PoisonPill
        Akka.system.actorSelection("user/TuktuScheduler") ! PoisonPill
        Akka.system.actorSelection("user/FlowManager") ! PoisonPill
        Akka.system.actorSelection("user/TuktuHealthChecker") ! PoisonPill
        Akka.system.actorSelection("user/TuktuDispatcher") ! Broadcast(PoisonPill)

        // Call onStop for module globals too
        for (moduleGlobal <- moduleGlobals) moduleGlobal.onStop(app)
    }
}