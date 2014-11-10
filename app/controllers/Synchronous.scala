package controllers

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.ActorIdentity
import akka.actor.Identify
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller

object Synchronous extends Controller {
    implicit val timeout = Timeout(1 seconds)
    
	def load(id: String) = Action { implicit request =>
	    // Get a syncDispatcher
	    try {
    	    // Send this to our analytics async handler
    	    val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? Identify(None)
            val dispActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
            // Get the futures we are after
    	    val jsFutureFuture = dispActor ? new syncDispatchRequest(id, None, false, false)
    	    val jsFuture = Await.result(jsFutureFuture.mapTo[scala.concurrent.Future[Enumerator[Map[String, JsValue]]]], 1 seconds)
    	    // Get the actual result
    	    val jsResult = Await.result(jsFuture.mapTo[Enumerator[Map[String, JsValue]]], 1 seconds)
    	    
    	    // We use an enumeratee to convert the map into a chunked result
    	    val jsEnumeratee: Enumeratee[Map[String, JsValue], String] = Enumeratee.map(mapElem => (mapElem("js")).asOpt[String].getOrElse(""))
    	    // TODO: Once all JS is streamed, we need to make a subsequent call to Tuktu
    	    val jsEnumerator: Enumerator[Map[String, JsValue]] = Enumerator(Map("js" -> Json.parse("""
    	            
    	    """).as[JsString]))
    	    Ok.chunked(jsResult.andThen(jsEnumerator).through(jsEnumeratee))
	    } catch {
    	    case e: TimeoutException => {Ok("")} // Fail
    	    case e: NullPointerException => {Ok("")}
    	}
	}
}