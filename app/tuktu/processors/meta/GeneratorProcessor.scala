package tuktu.processors.meta

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import akka.actor.Props
import play.api.libs.concurrent.Akka
import tuktu.api.utils.evaluateTuktuString
import tuktu.api.BufferProcessor
import tuktu.processors.BufferActor
import tuktu.api.StopPacket
import scala.concurrent.Await

/**
 * Wraps a generator in a processor and starts running it as soon as one data packet has been obtained
 */
class GeneratorProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Set up buffer actor
    val bufferActor = Akka.system.actorOf(Props(classOf[BufferActor], genActor))
    bufferActor ! new InitPacket
    
    var generatorName: String = _
    var generatorConfig: JsObject = _
    
    override def initialize(config: JsObject) {
        generatorName = (config \ "generator_name").as[String]
        generatorConfig = (config \ "generator_config").as[JsObject]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        
        data
    }) compose Enumeratee.onEOF(() => bufferActor ! new StopPacket)
}