package tuktu.processors.bucket.aggregate

import play.api.libs.json.JsObject
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.utils.TuktuArithmeticsParser

/**
 * Aggregates by key for a given set of keys and an arithmetic expression to compute
 * (can contain aggregation functions
 */
class AggregateByKeyProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    var expression: String = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
        expression = (config \ "expression").as[String]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        if (data.size == 0) List()
        else {
            val parser = new TuktuArithmeticsParser(data)
            // Go over all paths
            List((for (field <- fields) yield {
                // Peplace functions with field names
                val newExpression = parser.allowedFunctions.foldLeft(expression)((a, b) => {
                    a.replaceAll(b, b + "(" + field + ")")
                })
                // Evaluate string
                field -> parser(newExpression)
            }).toMap)
        }
    }
}