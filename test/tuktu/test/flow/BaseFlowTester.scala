package tuktu.test.flow

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import controllers.Dispatcher
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import scala.concurrent.Future

class BaseFlowTesterCollector() extends Actor with ActorLogging {
    val buffer = collection.mutable.ListBuffer.empty[DataPacket]
    def receive() = {
        case dp: DataPacket => buffer += dp
        case sp: StopPacket => {}
    }
}

class EnumForwarder(actor: ActorRef) {
    def apply(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((dp: DataPacket) => {
        actor ! dp
        dp
    }) compose Enumeratee.onEOF(() => {
        actor ! new StopPacket
    })
}

class BaseFlowTester {
    def apply(outputs: List[List[DataPacket]], flowName: String): Future[Boolean] = {
        // Open the file and pass on
        val configFile = scala.io.Source.fromFile(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs") +
                "/" + flowName + ".json", "utf-8")
        val cfg = Json.parse(configFile.mkString).as[JsObject]
        configFile.close
        apply(outputs, cfg)
    }
    
    def apply(outputs: List[List[DataPacket]], config: JsObject): Future[Boolean] = Future {
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
        val processorEnumeratees = for ((procEnum, index) <- Dispatcher.buildEnums(next, processorMap, None)._2.zipWithIndex) yield {
            // Create actor that will fetch the results
            val collectionActor = Akka.system.actorOf(Props(classOf[BaseFlowTesterCollector]),
                    name = "testActor_" + java.util.UUID.randomUUID.toString)

            // Append enumeratee with actor sending functionality
            procEnum compose new EnumForwarder(collectionActor)()
        }
        
        // Run the flow for each branch
        
        
        true
    }
}