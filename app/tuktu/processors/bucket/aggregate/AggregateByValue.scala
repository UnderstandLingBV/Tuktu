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
    var base: String = _
    // The expression, most of the time it probably is just min(), max(), count(), etc. (see TuktuArithmeticsParser for available functions), but can be combined
    var expression: String = _
    var evaluatedExpression: Option[String] = None

    override def initialize(config: JsObject) {
        group = (config \ "group").as[List[String]]
        base = (config \ "base_value").as[String]
        expression = (config \ "expression").as[String]
    }

    /**
     * @param data A DataPacket's data
     * @return Cube values (to write to result) and Map(cubeStringRepresentation -> evaluated expression)
     */
    private def preprocess(data: List[Map[String, Any]]): List[(List[Any], Map[String, Any])] = {
        (for (
            datum <- data;
            options = group map { field => utils.fieldParser(datum, utils.evaluateTuktuString(field, datum)) } if options.forall { _.isDefined }
        ) yield {
            // Evaluate expression
            if (evaluatedExpression == None)
                evaluatedExpression = Some(utils.evaluateTuktuString(expression, datum))

            // Get value to use
            val values = options map { _.get }
            val jsStrings = values map {
                _ match {
                    case v: JsString => v
                    case v: Any      => new JsString(v.toString)
                }
            }
            (values, Map(jsStrings.mkString(",") -> ArithmeticParser(utils.evaluateTuktuString(base, datum))))
        })
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        DataPacket({
            // We have to get all the values for this key over all data
            val baseValues = preprocess(data.data)

            // Create the parse for this field
            val parser = new TuktuArithmeticsParser(baseValues.map(_._2))

            // Get all values
            val allValues = baseValues map { _._1 } distinct

            // Compute stuff
            for (values <- allValues) yield {
                val jsStrings = values map {
                    _ match {
                        case v: JsString => v
                        case v: Any      => new JsString(v.toString)
                    }
                }
                // Peplace functions with field value names
                val newExpression = parser.allowedFunctions.foldLeft(evaluatedExpression.get)((a, b) => {
                    a.replace(b + "()", b + "(" + jsStrings.mkString(",") + ")")
                })

                // Build nested result: split by '.' and nest the whole way down; then mergeMaps
                def buildResult(tuples: List[(String, Any)], current: Map[String, Any] = Map.empty): Map[String, Any] = tuples match {
                    case Nil => current
                    case head :: tail => {
                        def helper(path: List[String], value: Any): Map[String, Any] = path match {
                            case head :: Nil => Map(head -> value)
                            case head :: tail => Map(head -> helper(tail, value))
                        }
                        buildResult(tail, utils.mergeMap(current, helper(head._1.split('.').toList, head._2)))
                    }
                }
                buildResult(group.zip(values) ++ List(resultName -> parser(newExpression)))
            }
        })
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