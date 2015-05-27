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

object Global extends GlobalSettings {
    implicit val timeout = Timeout(5 seconds)
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
	}
}