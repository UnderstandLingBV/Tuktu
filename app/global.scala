import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import controllers.Dispatcher
import monitor.DataMonitor
import play.api.Application
import play.api.GlobalSettings
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.RequestHeader
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.InternalServerError
import play.api.mvc.Results.NotFound
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object Global extends GlobalSettings {
    implicit val timeout = Timeout(1 seconds)
    /**
     * Load this on startup. The application is given as parameter
     */
	override def onStart(app: Application) {
        // Set up monitoring actor
        val monActor = Akka.system.actorOf(Props[DataMonitor], name = "TuktuMonitor")
        monActor ! "init"
        // Set up dispatcher
		val dispActor = Akka.system.actorOf(Props(classOf[Dispatcher], monActor), name = "TuktuDispatcher")
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
	
	/*override def onStop(app: Application) {
	    
	}*/
}