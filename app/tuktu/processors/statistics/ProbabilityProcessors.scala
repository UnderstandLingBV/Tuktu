package tuktu.processors.statistics

import tuktu.api.BaseProcessor
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Assigns a number to each datapacket with a given probability
 */
class NumberWithProbabilityProcessor(resultName: String) extends BaseProcessor(resultName) {
    var numbersWithProbability = List.empty[(Int, Double)]
        
    override def initialize(config: JsObject) {
        // Get the numbers we need and the probability of outputting them
        val numbers = (config \ "numbers").as[List[JsObject]]
        numbersWithProbability = {
            val unnormalized = (for (number <- numbers) yield
                (number \ "number").as[Int] -> (number \ "probability").as[Double]).toMap
            // Normalize
            val sum = unnormalized.values.sum
            unnormalized.map(elem => elem._1 -> elem._2 / sum).toList
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> getNumber(numbersWithProbability(0)._2, Math.random(), 0))
        }
    })
    
    def getNumber(accum: Double, prob: Double, offset: Int): Int = {
        if (prob < accum)
            // Found it, return the number
            numbersWithProbability(offset)._1
        else
            // We need to continue with the next number
            getNumber(accum + numbersWithProbability(offset)._2, prob, offset + 1)
    }
}