package tuktu.processors.bucket.concurrent.statistics

import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor
import akka.actor.ActorRef
import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee

/**
 * Computes the mean of a field of a dataset in a concurrent way
 */
class MeanProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                List(
                        "tuktu.processors.bucket.aggregate.SumProcessor",
                        "tuktu.processors.bucket.aggregate.CountProcessor"
                ),
                List(config, config)
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}