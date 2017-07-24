package tuktu.processors.bucket.aggregate

import play.api.libs.json.{ Json, JsArray, JsObject, JsString }
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.api.Parsing._
import tuktu.api.utils
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import scala.concurrent.Future

/**
 * Aggregates by key for a given set of keys and an arithmetic expression to compute
 * (can contain aggregation functions)
 */
class AggregateByValueProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    // First group distinct values within these fields and retain them in the result (since they are all the same); everything else will be dropped
    var group: List[String] = _
    // The base value for each distinct value; for count() this is probably 1; for everything else it probably is the value of the ${field} you want the expression to be executed on
    var base: utils.evaluateTuktuString.TuktuStringRoot = _
    // Base key constant used internally
    val baseKey: String = "b"
    // The expression, most of the time it probably is just min(), max(), count(), etc. (see ArithmeticParser for available functions), but can be combined
    var expression: utils.evaluateTuktuString.TuktuStringRoot = _
    var evaluatedExpression: Option[String] = None

    override def initialize(config: JsObject) {
        group = (config \ "group").as[List[String]]
        base = utils.evaluateTuktuString.prepare((config \ "base_value").as[String])
        expression = utils.evaluateTuktuString.prepare((config \ "expression").as[String])
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM { data =>
        if (evaluatedExpression.isEmpty && data.nonEmpty) {
            evaluatedExpression =
                Some(ArithmeticParser.allowedFunctions.foldLeft(
                    utils.evaluateTuktuString(expression, data.data.head)) {
                        (a, b) => a.replace(b + "()", b + "(" + JsString(baseKey).toString + ")")
                    })
        }

        Future { DataPacket(doProcess(data.data)) }
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // TODO: Look into whether this works in a concurrent setting
        // We have to get all the values for this key over all data
        val groups = data.map {
            datum => (Map(baseKey -> ArithmeticParser(base.evaluate(datum))), datum)
        }.groupBy {
            case (base, datum) => group.map { key => key -> utils.fieldParser(datum, key) }
        }

        groups.map {
            case (values, list) =>
                val d = list.map(_._1)

                // Build nested result: split by '.' and nest the whole way down; then mergeMaps
                def buildResult(tuples: List[(String, Any)], current: Map[String, Any] = Map.empty): Map[String, Any] = tuples match {
                    case Nil => current
                    case head :: tail => {
                        def helper(path: List[String], value: Any): Map[String, Any] = path match {
                            case head :: Nil  => Map(head -> value)
                            case head :: tail => Map(head -> helper(tail, value))
                        }
                        buildResult(tail, utils.mergeMap(current, helper(head._1.split('.').toList, head._2)))
                    }
                }
                buildResult((for ((key, Some(value)) <- values) yield key -> value) ++ List(resultName -> ArithmeticParser(evaluatedExpression.get, d)))
        }.toList
    }
}