package tuktu.processors.bucket.concurrent.statistics

import tuktu.processors.bucket.concurrent.BaseConcurrentProcessor
import akka.actor.ActorRef
import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee

object NumericHelper {
    def toDouble(elem: Any) = {
        elem match {
            case a: String => elem.asInstanceOf[String].toDouble
            case a: Int => elem.asInstanceOf[Int].toDouble
            case a: Integer => elem.asInstanceOf[Integer].toDouble
            case a: Long => elem.asInstanceOf[Long].toDouble
            case a: Float => elem.asInstanceOf[Float].toDouble
            case _ => elem.asInstanceOf[Double]
        }
    }
}

/**
 * Computes the mean of a field of a dataset in a concurrent way
 */
class MeanProcessor(genActor: ActorRef, resultName: String) extends BaseConcurrentProcessor(genActor, resultName) {
    var field = ""

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        // Initialize
        this.initializeNodes(
                (config \ "nodes").as[List[String]],
                List(
                        "tuktu.processors.bucket.aggregate.SumProcessor",
                        "tuktu.processors.bucket.aggregate.CountProcessor"
                ),
                List(config, config),
                mergeProcess
        )
    }

    def mergeProcess(): List[List[Map[String, Any]]] => DataPacket = results => {
        // Sum is first, count second
        val iter = results(0).zip(results(1))
        new DataPacket({
            for (item <- iter) yield Map(field -> (NumericHelper.toDouble(item._1(field)) / NumericHelper.toDouble(item._2(field))))
        })
    }
}