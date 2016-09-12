package tuktu.processors.meta

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api.BaseProcessor
import tuktu.api.BufferProcessor
import tuktu.api.DataPacket
import tuktu.api.utils.evaluateTuktuString
import tuktu.api.StopPacket
import akka.routing.Broadcast

/**
 * Helper class to have the generator wrapper deal with the data
 */
class WrapperHelper(originalData: Map[String, Any], generatorName: String,
        resultName: String, remoteGenerator: ActorRef, asWhole: Boolean) extends Actor with ActorLogging {
    val buffer = collection.mutable.ListBuffer.empty[List[Map[String, Any]]]
    
    // Make the helper enumeratee
    def resultFetchingEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Buffer or not?
        if (asWhole) buffer += data.data
        else remoteGenerator ! data
        
        data
    }) compose Enumeratee.onEOF(() => {
        // Forward if whole data was set to true
        if (asWhole)
            remoteGenerator ! DataPacket(List(originalData +
                    (resultName -> buffer.toList)
            ))
        
        // Terminate
        remoteGenerator ! Broadcast(new StopPacket)
    })
    
    def receive() = {
        case config: JsValue => {
            // Set up the generator
            val clazz = Class.forName(generatorName)
            val actorRef = Akka.system.actorOf(Props(clazz, resultName, List(resultFetchingEnumeratee), Some(self)))
            
            // Send config
            actorRef ! config.asInstanceOf[JsObject]
        }
    }
}

/**
 * Wraps a generator in a processor and starts running it as soon as one data packet has been obtained
 * @TODO: Make the merge strategy optional/configurable
 */
class GeneratorWrapperProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var generatorName: String = _
    var generatorConfig: JsObject = _
    var asWhole: Boolean = _
    
    override def initialize(config: JsObject) {
        generatorName = (config \ "generator_name").as[String]
        generatorConfig = (config \ "generator_config").as[JsObject]
        asWhole = (config \ "as_whole").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.data.foreach(datum => {
            // Create the actor and kickstart it
            val wrapperHelper = Akka.system.actorOf(Props(classOf[WrapperHelper], datum, generatorName,
                    resultName, genActor, asWhole))
            // Start
            wrapperHelper ! Json.parse(evaluateTuktuString(generatorConfig.toString, datum))
        })
        
        data
    })
}