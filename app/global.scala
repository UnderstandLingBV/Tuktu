import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.mvc.Results._
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.util.Timeout
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import controllers.Dispatcher

object Global extends GlobalSettings {
    implicit val timeout = Timeout(1 seconds)
    /**
     * Load this on startup. The application is given as parameter
     */
	override def onStart(app: Application) {
		val dispActor = Akka.system.actorOf(Props[Dispatcher], name = "TuktuDispatcher")
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