package tuktu.processors.bucket.aggregate

import play.api.libs.json.JsObject
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.utils.TuktuArithmeticsParser
import tuktu.api.utils
import tuktu.utils.ArithmeticParser
import play.api.libs.json.JsString
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import scala.concurrent.Future

/**
 * Aggregates by key for a given set of keys and an arithmetic expression to compute
 * (can contain aggregation functions)
 */
class AggregateByValueProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[JsObject] = _
    var expression: String = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[JsObject]]
        expression = (config \ "expression").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(
            if (data.isEmpty) List()
            else {
                // Go over all paths
                (for (obj <- fields) yield {
                    val field = (obj \ "field").as[String]
                    val base = (obj \ "base_value").as[String]
                    var evaluatedExpression: Option[String] = None

                    // We have to get all the values for this key over all data
                    val baseValues = data.data.collect {
                        case datum: Map[String, Any] if utils.fieldParser(datum, utils.evaluateTuktuString(field, datum)).isDefined => {
                            // Evaluate expression
                            if (evaluatedExpression == None)
                                evaluatedExpression = Some(utils.evaluateTuktuString(expression, datum))

                            // Get value to use
                            val value: String = utils.fieldParser(datum, utils.evaluateTuktuString(field, datum)).get match {
                                case v: JsString => v.value
                                case v: Any      => v.toString
                            }
                            Map(value -> ArithmeticParser(utils.evaluateTuktuString(base, datum)))
                        }
                    }

                    // Create the parse for this field
                    val parser = new TuktuArithmeticsParser(baseValues)

                    // Get all values
                    val allValues = baseValues.flatMap { _.keys }.distinct

                    // Compute stuff
                    (for (value <- allValues) yield {
                        // Peplace functions with field value names
                        val newExpression = parser.allowedFunctions.foldLeft(evaluatedExpression.get)((a, b) => {
                            a.replace(b + "()", b + "(" + value + ")")
                        })

                        // Evaluate string
                        value -> parser(newExpression)
                    }).toMap
                }).toList
            }
        )
    })

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        if (data.isEmpty) List()
        else {
            // Create the parser
            val parser = new TuktuArithmeticsParser(data)

            // Get all values
            val allValues = data.flatMap(_.keys).distinct

            // Compute stuff
            List((for (value <- allValues) yield {
                // Peplace functions with field value names
                val newExpression = parser.allowedFunctions.foldLeft(expression)((a, b) => {
                    a.replace(b + "()", b + "(" + value + ")")
                })

                // Evaluate string
                value -> parser(newExpression)
            }).toMap)
        }
    }
}