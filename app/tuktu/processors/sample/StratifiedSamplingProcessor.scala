package tuktu.processors.sample

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/**
 * Performs (potentially random) stratified sampling
 */
class StratifiedSamplingProcessor(resultName: String) extends BaseProcessor(resultName) {
    var classField: String = _
    var random: Boolean = _
    var sampleCount: Option[Int] = _
    
    override def initialize(config: JsObject) {
        classField = (config \ "class_field").as[String]
        random = (config \ "random").asOpt[Boolean].getOrElse(false)
        sampleCount = (config \ "sample_count").asOpt[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        DataPacket({
            // Group items together based on class field
            val grouped = data.data.groupBy(datum => datum(classField))
            // Do the counting
            val classCounts = grouped.map(el => el._1 -> el._2.size)
            val minCount = classCounts.minBy(_._2)._2
            // Get the right samples
            grouped.flatMap(group => {
                (if (random) Random.shuffle(group._2) else group._2).take(sampleCount match {
                    case Some(s) => Math.min(minCount, s)
                    case None => minCount
                })
            }).toList
        })
    })
}