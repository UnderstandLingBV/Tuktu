import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.PoisonPill
import akka.actor.Props
import akka.routing.SmallestMailboxPool
import akka.routing.Broadcast
import akka.util.Timeout
import controllers.Dispatcher
import monitor.DataMonitor
import play.api.Application
import play.api.GlobalSettings
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.NotFound
import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.libs.json.Json
import org.reflections.Reflections
import scala.collection.JavaConverters._
import controllers.TuktuScheduler
import tuktu.api.TuktuGlobal
import controllers.AutoStart

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

        moduleGlobalClasses.foreach(moduleGlobal => try {
                moduleGlobals += moduleGlobal.newInstance().asInstanceOf[TuktuGlobal]
            } catch {
                case e: Exception => {
                    System.err.println("Failed loading Global of " + moduleGlobal.getName)
                    e.getMessage
                }
            }
        )
    }
    
    /**
     * Load this on startup. The application is given as parameter
     */
	override def onStart(app: Application) {
        // Set timeout
        Cache.set("timeout", Play.current.configuration.getInt("tuktu.timeout").getOrElse(5))
        
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
        monActor ! "init"
                
        // Load module globals
        LoadModuleGlobals(app)
        moduleGlobals.foreach(moduleGlobal => moduleGlobal.onStart(app))
        
        // Already start running jobs as defined in the autostart file
        AutoStart
	}
    
    /**
	 * Overwrite internal server error page (code 500)
	 */
	override def onError(request: RequestHeader, ex: Throwable) = {
		Future.successful(InternalServerError(
	    		Json.obj("error" -> "Internal server error")
	    ))
	}
	
	/**
	 * Overwrite not found error page (code 404)
	 */
	override def onHandlerNotFound(request: RequestHeader) = {
		Future.successful(NotFound(
				Json.obj("error" -> "API endpoint not found")
		))
	}
	
	/**
	 * Overwrite bad request error page (route found, but no binding)
	 */
	override def onBadRequest(request: RequestHeader, error: String) = {
		Future.successful(BadRequest(
		        Json.obj("error" -> "API endpoint not found")
		))
	}
	
	override def onStop(app: Application) {
	    // Terminate our dispatchers and monitor
        Akka.system.actorSelection("user/TuktuMonitor") ! PoisonPill
        Akka.system.actorSelection("user/TuktuDispatcher") ! Broadcast(PoisonPill)
        
        // Call onStop for module globals too
        moduleGlobals.foreach(moduleGlobal => moduleGlobal.onStop(app))
	}
}