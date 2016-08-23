package tuktu.processors.bucket.concurrent.aggregate

import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor
import akka.actor.ActorRef
import play.api.libs.json.JsObject

/**
 * Concurrently aggregates by key. Only works for commutative and associative functions (ie. (a+b)+c = a+(b+c) and a+b=b+a) 
 */
class AggregateByValueProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) {
        // Initialize
        this.initializeNodes(
            (config \ "nodes").as[List[String]],
            "tuktu.processors.bucket.aggregate.AggregateByValueProcessor",
            config,
            null,
            true)
    }
}