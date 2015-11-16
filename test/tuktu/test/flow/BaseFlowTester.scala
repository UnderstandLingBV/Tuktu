package tuktu.test.flow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.TestKit
import controllers.Dispatcher
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import play.api.libs.json.JsValue
import akka.actor.PoisonPill
import akka.actor.Cancellable
import tuktu.test.testUtil

case class ResultPacket()
case class CheckPacket(sender: ActorRef, iteration: Int)

/**
 * Actor that will collect data packets
 */
class BaseFlowTesterCollector() extends Actor with ActorLogging {
    val buffer = collection.mutable.ListBuffer.empty[DataPacket]
    var done = false
    var schedulerActor: Cancellable = null
    
    def receive() = {
        case dp: DataPacket => buffer += dp
        case sp: StopPacket => done = true
        case rp: ResultPacket => {
            if (done) {
                sender ! buffer
                self ! PoisonPill
            }
            else {
                schedulerActor = Akka.system.scheduler.schedule(
                    1000 milliseconds,
                    1000 milliseconds,
                    self,
                    new CheckPacket(sender, 0))
            }
        }
        case cp: CheckPacket => {
            schedulerActor.cancel
            if (done) {
                cp.sender ! buffer
                self ! PoisonPill
            }
            else {
                if (cp.iteration == 3) {
                    // Tried too long, fail
                    cp.sender ! null
                    self ! PoisonPill
                }
                else {
                    // Try again
                    schedulerActor = Akka.system.scheduler.schedule(
                        1000 milliseconds,
                        1000 milliseconds,
                        self,
                        new CheckPacket(cp.sender, cp.iteration + 1))
                }
            }
        }
    }
}

/**
 * Class to forward data as if it were a processor
 */
class EnumForwarder(actor: ActorRef) {
    def apply(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((dp: DataPacket) => {
        actor ! dp
        dp
    }) compose Enumeratee.onEOF(() => {
        actor ! new StopPacket
    })
}

/**
 * Base flow tester class, should be invoked for testing flows
 */
class BaseFlowTester(timeoutSeconds: Int = 5) extends TestKit(ActorSystem("test")) {
    implicit val timeout = Timeout(timeoutSeconds seconds)
    
    def apply(outputs: List[List[DataPacket]], flowName: String): Future[Boolean] = {
        // Open the file and pass on
        val configFile = scala.io.Source.fromFile(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs") +
                "/" + flowName + ".json", "utf-8")
        val cfg = Json.parse(configFile.mkString).as[JsObject]
        configFile.close
        apply(outputs, cfg)
    }
    
    /**
     * Executes a flow to capture its output and match it with a set of expected outputs
     */
    def apply(outputs: List[List[DataPacket]], config: JsObject): Future[Boolean] = {
        // Build processor map
        val processorMap = Dispatcher.buildProcessorMap((config \ "processors").as[List[JsObject]])
        
        // Get the data generators
        val generator = (config \ "generators").as[List[JsObject]].head
        // Get all fields
        val generatorName = (generator \ "name").as[String]
        val generatorConfig = (generator \ "config").as[JsObject]
        val resultName = (generator \ "result").as[String]
        val next = (generator \ "next").as[List[String]]
        
        // Build the processor pipeline for this generator
        val (enums, actors) = {
            val enumActors = for ((procEnum, index) <- Dispatcher.buildEnums(next, processorMap, None, "")._2.zipWithIndex) yield {
                // Create actor that will fetch the results
                val collectionActor = Akka.system.actorOf(Props(classOf[BaseFlowTesterCollector]),
                        name = "testActor_" + java.util.UUID.randomUUID.toString)
    
                // Append enumeratee with actor sending functionality
                (
                        procEnum compose new EnumForwarder(collectionActor)(),
                        collectionActor
                )
            }
            
            (enumActors.map(_._1), enumActors.map(_._2)) 
        }
        
        // Set up the generator
        val clazz = Class.forName(generatorName)
        // Run the flow
        val flow = Akka.system.actorOf(Props(clazz, resultName, enums, None),
            name = clazz.getName +  "_" + java.util.UUID.randomUUID.toString
        )
        flow ! new InitPacket
        flow ! generatorConfig
        
        // Ask all the actors for completion
        val results = Future.sequence(for (actor <- actors) yield (actor ? new ResultPacket()).asInstanceOf[Future[List[DataPacket]]])
        
        // Inspect the results
        results.map(obtainedOutput => {
            if (obtainedOutput == null)
                false
            else {
                // Compare data packet by data packet
                obtainedOutput.zip(outputs).forall(packetLists => {
                    val obtainedList = packetLists._1
                    val expectedList = packetLists._2
                    
                    // Inspect the next level
                    obtainedList.zip(expectedList).forall(packets => {
                        val obtained = packets._1
                        val expected = packets._2
                        
                        // Inspect the data inside the packets
                        obtained.data.zip(expected.data).forall(data => testUtil.inspectMaps(data._1, data._2))
                    })
                })
            }
        })
    }
}