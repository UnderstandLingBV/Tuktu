package tuktu.ml.processors.association

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

import smile.association

/**
 * Learns association rules on batched data
 */
class ARMProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var minSupport: Int = _
    var minConf: Double = _
    
    override def initialize(config: JsObject) = {
       field = (config \ "field").as[String]
       minSupport = (config \ "min_support").as[Int]
       minConf = (config \ "min_conf").as[Double]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val itemsets = (for (datum <- data.data) yield datum(field).asInstanceOf[Seq[Int]].toArray).toArray
        
        val arm = new association.ARM(itemsets, minSupport)
        val rules = arm.learn(minConf)
        
        new DataPacket(rules.map(rule => Map(
                resultName + "_antecedent" -> rule.antecedent,
                resultName + "_consequent" -> rule.consequent,
                resultName + "_confidence" -> rule.confidence
        )).toList)
    })
}