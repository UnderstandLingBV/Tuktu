package tuktu.utils

import fastparse.WhitespaceApi
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import tuktu.processors.bucket.statistics.StatHelper
import scala.util.{ Try, Success, Failure }

/**
 * Performs arithmetics over a string representation
 */
object ArithmeticParser {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._

    // Allow all sorts of numbers, negative and scientific notation
    val number: P[Double] = P(CharIn('-' :: '.' :: 'e' :: ('0' to '9').toList).rep(1).!.map(_.toDouble))
    val parens: P[Double] = P("(" ~/ addSub ~ ")")
    val factor: P[Double] = P(number | parens)

    val divMul: P[Double] = P(factor ~ (CharIn("*/").! ~/ factor).rep).map(eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(eval)
    val expr: P[Double] = P((addSub | factor) ~ End)

    def eval(tree: (Double, Seq[(String, Double)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "+" => left + right case "-" => left - right
                case "*" => left * right case "/" => left / right
            }
        }
    }

    def apply(str: String) = {
        expr.parse(str).get.value
    }
}

/**
 * Parses Boolean predicates
 */
object PredicateParser {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._

    // Boolean base logic
    val literal: P[Boolean] = P(("true" | "false").!.map(_.toBoolean))
    val parens: P[Boolean] = P("(" ~/ (andOr | arithExpr) ~ ")")
    val neg: P[Boolean] = P(("!(" ~/ andOr ~ ")") | ("!" ~/ literal)).map(!_)
    val factor: P[Boolean] = P(literal | neg | parens | arithExpr | stringExpr)
    // Strings
    val strings: P[String] = P(CharIn(('a' to 'z').toList ++ ('A' to 'Z').toList).rep(1).!.map(_.toString))

    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithExpr: P[Boolean] = P((ArithmeticParser.addSub | ArithmeticParser.parens) ~/ ("<" | ">" | ">=" | "<=" | "==" | "!=").! ~ (ArithmeticParser.addSub | ArithmeticParser.parens))
        .map {
            case (left, op, right) => op match {
                case "<" => left < right case ">" => left > right
                case "<=" => left <= right case ">=" => left >= right
                case "==" => left == right case "!=" => left != right
            }
        }

    // Evaluate string expressions
    val stringExpr: P[Boolean] = P(strings ~/ ("==" | "!=").! ~ strings)
        .map {
            case (left, "==", right) => left == right
            case (left, "!=", right) => left != right
        }

    // Logical and/or
    val andOr: P[Boolean] = P(factor ~ (("&&" | "||").! ~/ factor).rep).map(eval)
    val expr: P[Boolean] = P(andOr ~ End)

    def eval(tree: (Boolean, Seq[(String, Boolean)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "&&" => left && right case "||" => left || right
            }
        }
    }

    def apply(str: String) = {
        expr.parse(str).get.value
    }
}

/**
 * Parses Boolean predicates over a datum
 */
class TuktuPredicateParser(datum: Map[String, Any]) {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._

    // Strings
    val strings: P[String] = P(CharIn(('a' to 'z').toList ++ ('A' to 'Z').toList ++ List('_', '-', '.', ',')).rep(1).!.map(_.toString))
    // All Tuktu-defined functions
    val functions: P[Boolean] = P(
            (
                    (
                        "containsFields(" | "isNumeric(" | "isNull(" | "isJSON("
                    ).! ~/ strings ~ ")"
            ) | (
                    ("isEmpty(".! ~/ ")".!)
            )
        ).map {
            case ("containsFields(", fields) => fields.split(',').forall { string =>
                // Get the path and evaluate it against the datum
                val path = string.split('.').toList
                tuktu.api.utils.fieldParser(datum, path).isDefined
            }
            case ("isNumeric(", field) => Try {
                    StatHelper.anyToDouble(datum(field))
                } match {
                    case Success(double) => true
                    case Failure(_throw) => false
                }
            case ("isNull(", field) => datum(field) match {
                case null => true
                case _    => false
            }
            case ("isJSON(", fields) => {
                fields.split(',').forall { string =>
                    // Get the path, evaluate it against the datum, and check if it's JSON
                    val path = string.split('.').toList
                    val result = tuktu.api.utils.fieldParser(datum, path)
                    result.map { res => res.isInstanceOf[JsValue] }.getOrElse(false)
                }
            }
            case ("isEmpty(", ")") => datum.isEmpty
        }
    // Boolean base logic
    val literal: P[Boolean] = P(("true" | "false").!.map(_.toBoolean))
    val parens: P[Boolean] = P("(" ~/ (andOr | arithExpr) ~ ")")
    val neg: P[Boolean] = P(("!(" ~/ andOr ~ ")") | ("!" ~/ literal)).map(!_)
    val factor: P[Boolean] = P(literal | neg | parens | functions | stringExpr | arithExpr)

    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithExpr: P[Boolean] = P((ArithmeticParser.addSub | ArithmeticParser.parens) ~/ ("<" | ">" | ">=" | "<=" | "==" | "!=").! ~ (ArithmeticParser.addSub | ArithmeticParser.parens))
        .map {
            case (left, op, right) => op match {
                case "<" => left < right case ">" => left > right
                case "<=" => left <= right case ">=" => left >= right
                case "==" => left == right case "!=" => left != right
            }
        }

    // Evaluate string expressions
    val stringExpr: P[Boolean] = P(strings ~/ ("==" | "!=").! ~ strings)
        .map {
            case (left, "==", right) => left == right
            case (left, "!=", right) => left != right
        }

    // Logical and/or
    val andOr: P[Boolean] = P(factor ~ (("&&" | "||").! ~/ factor).rep).map(eval)
    val expr: P[Boolean] = P(andOr ~ End)

    def eval(tree: (Boolean, Seq[(String, Boolean)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "&&" => left && right case "||" => left || right
            }
        }
    }

    def apply(str: String): Boolean = {
        expr.parse(str).get.value
    }
}

/**
 * Performs arithmetics and aggregations over entire DataPackets
 */
class TuktuArithmeticsParser(data: List[Map[String, Any]]) {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._

    // List of allowed functions
    def allowedFunctions = List(
            "count", "avg", "median", "sum", "max", "min"
    )
    
    def parsePath(datum: Map[String, Any], field: String): Option[Any] = {
        if (datum.contains(field)) tuktu.api.utils.fieldParser(datum, List(field), None)
        else tuktu.api.utils.fieldParser(datum, field.split('.').toList, None)
    }
    
    // Strings
    val strings: P[String] = P(CharIn(('a' to 'z').toList ++ ('A' to 'Z').toList ++ List('_', '-', '.')).rep(1).!.map(_.toString))
    // All Tuktu-defined arithmetic functions
    val functions: P[Double] = P(
            (
                    (
                        "avg(" | "median(" | "sum(" | "max(" | "min(" | "count("
                    ).! ~/ strings ~ ")"
            )
        ).map {
            case ("avg(", field) => {
                val (sum, count) = data.foldLeft((0.0, 0.0))((a,b) => {
                    // Get the value of the field we are after
                    val optionValue = parsePath(b, field)
                    val value = optionValue match {
                        case Some(v) => StatHelper.anyToDouble(v)
                        case _ => 0.0
                    }
                    (a._1 + value, a._2 + (optionValue match {
                        case Some(v) => 1.0
                        case _ => 0.0
                    }))
                })
                sum / count
            }
            case ("median(", field) => {
                val sortedData = (data.collect {
                    case datum: Map[String, Any] if parsePath(datum, field) != None => {
                        StatHelper.anyToDouble(parsePath(datum, field).get)
                    }
                }).sorted

                // Find the mid element
                val n = sortedData.size
                if (n % 2 == 0) {
                    // Get the two elements and average them
                    val n2 = n / 2
                    val n1 = n2 - 1
                    (sortedData(n1) + sortedData(n2)) / 2
                } else
                    sortedData((n - 1) / 2)
            }
            case ("sum(", field) => data.foldLeft(0.0)((a,b) => a + StatHelper.anyToDouble(
                    parsePath(b, field) match {
                        case Some(v) => StatHelper.anyToDouble(v)
                        case _ => 0.0
                    }
            ))
            case ("max(", field) => {
                val maxElem = data.maxBy(datum => StatHelper.anyToDouble(
                        parsePath(datum, field) match {
                            case Some(v) => StatHelper.anyToDouble(v)
                            case _ => Double.MinValue
                        }
                ))
                StatHelper.anyToDouble(maxElem(field))
            }
            case ("min(", field) => {
                val maxElem = data.minBy(datum => StatHelper.anyToDouble(
                        parsePath(datum, field) match {
                            case Some(v) => StatHelper.anyToDouble(v)
                            case _ => Double.MaxValue
                        }
                ))
                StatHelper.anyToDouble(maxElem(field))
            }
            case ("count(", field) if field.isEmpty => data.foldLeft(0)((a,b) => a + 1)
            case ("count(", field) => data.foldLeft(0)((a,b) => a + {
                if (parsePath(b, field) != null) 1 else 0
            })
        }

    // Allow all sorts of numbers, negative and scientific notation
    val number: P[Double] = P(CharIn('-' :: '.' :: 'e' :: ('0' to '9').toList).rep(1).!.map(_.toDouble))
    val parens: P[Double] = P("(" ~/ addSub ~ ")")
    val factor: P[Double] = P(number | parens | functions)

    val divMul: P[Double] = P(factor ~ (CharIn("*/").! ~/ factor).rep).map(eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(eval)
    val expr: P[Double] = P((addSub | factor) ~ End)

    def eval(tree: (Double, Seq[(String, Double)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "+" => left + right case "-" => left - right
                case "*" => left * right case "/" => left / right
            }
        }
    }

    def apply(str: String) = {
        expr.parse(str).get.value
    }
}