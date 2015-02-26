package tuktu.processors.bucket.concurrent.aggregate

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor
import akka.actor.ActorRef
import tuktu.api.DataPacket

/**
 * Gets the minimum of a stream in a distributed fashion
 */
class MinProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                "tuktu.processors.bucket.aggregate.MinProcessor",
                config
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}

/**
 * Gets the maximum of a stream in a distributed fashion
 */
class MaxProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                "tuktu.processors.bucket.aggregate.MaxProcessor",
                config
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}

/**
 * Gets the sum of a field of a stream in a distributed fashion
 */
class SumProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                "tuktu.processors.bucket.aggregate.SumProcessor",
                config
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}

/**
 * Gets the number of elements of a stream in a distributed fashion
 */
class CountProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    override def initialize(config: JsObject) = {
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                "tuktu.processors.bucket.aggregate.CountProcessor",
                config
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
}