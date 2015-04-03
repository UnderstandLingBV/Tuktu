package tuktu.processors.bucket.concurrent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import scala.collection.GenTraversableOnce
import akka.actor.ActorRef

/**
 * Removes duplicates in a distributed fashion
 */
class DeduplicationProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                "tuktu.processors.bucket.DeduplicationProcessor",
                config,
                null
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}