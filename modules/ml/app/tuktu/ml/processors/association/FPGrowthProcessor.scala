package tuktu.ml.processors.association

import tuktu.api.BaseProcessor
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import smile.association.FPGrowth
import scala.collection.JavaConversions._

/**
 * Performs FPGrowth on batched data to obtain frequent itemsets
 */
class FPGrowthProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var minSupport: Int = _
    
    override def initialize(config: JsObject) = {
       field = (config \ "field").as[String]
       minSupport = (config \ "min_support").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val itemsets = (for (datum <- data.data) yield datum(field).asInstanceOf[Seq[Int]].toArray).toArray
        
        val fpgrowth = new FPGrowth(itemsets, minSupport)
        val results = fpgrowth.learn
        
        DataPacket(results.map(itemSet => Map(
                resultName + "_items" -> itemSet.items,
                resultName + "_support" -> itemSet.support
        )).toList)
    })
}