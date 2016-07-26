package tuktu.test.flow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.Assertions._

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.ask
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.util.Timeout
import controllers.Dispatcher
import play.api.Play
import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import tuktu.test.testUtil

case class ResultPacket()
case class CheckPacket(sender: ActorRef, iteration: Int)

/**
 * Actor that will collect data packets
 */
class BaseFlowTesterCollector(as: ActorSystem) extends Actor with ActorLogging {
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
                schedulerActor = as.scheduler.schedule(
                    1000 milliseconds,
                    1000 milliseconds,
                    self,
                    new CheckPacket(sender, 0))
            }
        }
        case cp: CheckPacket => {
            schedulerActor.cancel
            if (done) {
                cp.sender ! buffer.toList
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
                    schedulerActor = as.scheduler.schedule(
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
class BaseFlowTester(as: ActorSystem, timeoutSeconds: Int = 5) extends TestKit(as) {
    implicit val timeout = Timeout(timeoutSeconds seconds)
    
    def apply(outputs: List[List[DataPacket]], flowName: String): Unit = {
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
    def apply(outputs: List[List[DataPacket]], config: JsObject): Unit = {
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
            val builtEnums = {
                val e = Dispatcher.buildEnums(next, processorMap, None, "", false)._2
                // Special case: check for empty processor list and simply add a dummy processor
                if (e.isEmpty) {
                    val dummy: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => dp)
                    List(dummy)
                }
                else e
            }
            
            val enumActors = for ((procEnum, index) <- builtEnums.zipWithIndex) yield {
                // Create actor that will fetch the results
                val collectionActor = as.actorOf(Props(classOf[BaseFlowTesterCollector], as),
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
        val flow = as.actorOf(Props(clazz, resultName, enums, None),
            name = clazz.getName +  "_" + java.util.UUID.randomUUID.toString
        )
        flow ! new InitPacket
        flow ! generatorConfig
        
        // Ask all the actors for completion
        val obtainedOutput = Await.result(
                Future.sequence(for (actor <- actors) yield (actor ? new ResultPacket()).asInstanceOf[Future[List[DataPacket]]]),
                timeout.duration
        )

        // Compare data packet by data packet
        val res = obtainedOutput.zip(outputs).forall(packetLists => {
            val obtainedList = packetLists._1
            val expectedList = packetLists._2
            
            // Inspect the next level
            obtainedList.zip(expectedList).forall(packets => {
                val obtained = packets._1
                val expected = packets._2
                
                // Inspect the data inside the packets
                (obtained.data.isEmpty && expected.data.isEmpty) ||
                    obtained.data.zip(expected.data).forall(data => testUtil.inspectMaps(data._1, data._2))
            })
        })
        
        assertResult(true, "Obtained output is:\r\n" + obtainedOutput + "\r\nExpected:\r\n" + outputs) {
            (obtainedOutput.isEmpty && outputs.isEmpty) || (!obtainedOutput.isEmpty && !outputs.isEmpty && res)
        }
    }
}