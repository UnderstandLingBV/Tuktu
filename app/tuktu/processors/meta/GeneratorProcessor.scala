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

class GeneratorProcessor(generator: ActorRef, resultName: String) extends BaseProcessor(resultName) {
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var generatorConfig: JsObject = _
    
    override def initialize(config: JsObject) {
        generatorConfig = (config \ "config").as[JsObject]
        (config \ "timeout").asOpt[Int] match {
            case None => {}
            case Some(to) => timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
        }
        
        // Send the initialize packet
        generator ! new InitPacket()
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Send the config and wait for response
        val generatorData = generator ? generatorConfig
        // Wait for the data to come in
        data
    })
}