package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.pattern.ask
import play.api.Play.current
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.validation.Constraints._
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.data.Form
import tuktu.api.DispatchRequest
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.Json
import akka.actor.ActorLogging
import play.api.cache.Cache
import akka.actor.Actor
import akka.actor.ActorRef
import akka.util.Timeout
import play.api.libs.json.JsArray
import play.api.libs.concurrent.Akka
import tuktu.api.AddMonitorEventListener
import scala.util.{Success, Failure}
import scala.concurrent.Await
import tuktu.api.AppMonitorPacket
import akka.actor.Props

object FlowManager extends Controller {
    
    // Retrieve the FlowManagerActor
    val flowManagerActor = Akka.system.actorSelection("user/FlowManager") 
    
    def overview() = Action { implicit request =>
        Ok(views.html.flowManager.overview(Map[String,List[String]]()))
    }
    
    case class flow(
        config: String
    )
    
    val flowForm = Form(
        mapping(
            "config" -> text.verifying(nonEmpty, minLength(2))            
        ) (flow.apply)(flow.unapply)
    )
    
    // kick off a simple scheduler
    def start() = Action { implicit request =>        
        //Bind
        flowForm.bindFromRequest.fold(
            formWithErrors => {                
                Redirect(routes.FlowManager.overview).flashing("error" -> "Some values were found incorrect.")
            },
            job => {
                val json = Json.parse(job.config).as[JsArray]
                flowManagerActor ! new FlowSettings(json)
            } )
        Redirect(routes.FlowManager.overview)
    }    
}

case class FlowSettings(
        config: JsArray
)

case class FlowDone(
        actorRef: ActorRef
)

class FlowManagerActor(actor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // collections of all the flows currently running
    val flows = scala.collection.mutable.Set[ActorRef]()

    def receive() = {
        // create new Flow
        case flow: FlowSettings => {
            flows.add(Akka.system.actorOf(Props(classOf[FlowActor], self, flow.config)))
        }
        // ActorRef is done
        case ref: FlowDone => {            
            flows.remove(ref.actorRef)
        }
        case _ => {}
    }
}

class FlowActor(parent: ActorRef, configJson: JsArray) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val config = configJson.as[List[List[String]]]
    // current working Row
    var currentRow = 0
    // Collection of Flows currently working on
    val currentFlows = scala.collection.mutable.Set[String]()
    // add this actor as event listener
    Akka.system.actorSelection("user/TuktuMonitor") ! new AddMonitorEventListener()
    // immediately start working
    runFlow(currentRow)

    // run flows in parallel
    def runFlow(row: Int) = {
        config(row).foreach { x =>
            {
                val generator = Await.result(
                    Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest(x, None, false, true, true, None),
                    timeout.duration).asInstanceOf[ActorRef]
                currentFlows.add(generator.path.toStringWithoutAddress)
            }
        }
    }

    // Act on a finished flow received from AppMonitor
    def finishedFlow(name: String) {
        currentFlows.remove(name)        
        if (currentFlows.isEmpty) {
            currentRow += 1
            if (currentRow >= config.size) {
                // All flows have finished, inform parent that we're done
                parent ! FlowDone(self)
            } else {
                runFlow(currentRow)
            }
        }
    }

    def receive() = {
        case appMonitor: AppMonitorPacket => {
            appMonitor.status match {
                case "done" => {
                    finishedFlow(appMonitor.getParentName)
                }
                case _ => {}
            }
        }
        case _ => {}
    }

}


