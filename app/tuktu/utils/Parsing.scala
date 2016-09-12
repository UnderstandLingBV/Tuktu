package tuktu.utils

import fastparse.WhitespaceApi
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import tuktu.api.utils.fieldParser
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
    val strings: P[String] = P(CharPred(_ != ')').rep(1).!.map(_.toString))
    // All Tuktu-defined functions
    val functions: P[Boolean] = P(
            (
                    (
                        "containsFields(" | "isNumeric(" | "isNull(" | "isJSON(" | "containsSubstring("
                    ).! ~/ strings ~ ")"
            ) | (
                    ("isEmpty(".! ~/ ")".!)
            )
        ).map {
            case ("containsFields(", fields) => fields.split(',').forall { path =>
                // Get the path and evaluate it against the datum
                fieldParser(datum, path).isDefined
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
                fields.split(',').forall { path =>
                    // Get the path, evaluate it against the datum, and check if it's JSON
                    val result = fieldParser(datum, path)
                    result.map { res => res.isInstanceOf[JsValue] }.getOrElse(false)
                }
            }
            case ("containsSubstring(", field) => {
                // Get the actual string and the substring
                val split = field.split(",")
                val string = split(0)
                val substring = split(1)
                string.contains(substring)
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
    def allowedFunctions = List("count", "avg", "median", "sum", "max", "min")

    // All Tuktu-defined arithmetic functions
    val functions: P[Double] = P(
            StringIn(allowedFunctions: _*).! ~/ "(" ~/ CharPred(_ != ')').rep(0).! ~ ")"
        ).map {
            case ("avg", field) => {
                val (sum, count) = data.foldLeft(0.0, 0) {
                    case ((sum, count), datum) =>
                        val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }
                        (
                            sum + v.getOrElse(0.0),
                            count + { if (v.isDefined) 1 else 0 }
                        )
                }

                if (count > 0)
                    sum / count
                else
                    0.0
            }
            case ("median", field) => {
                val sortedData = (for (datum <- data; v = fieldParser(datum, field) if v.isDefined) yield StatHelper.anyToDouble(v.get)).sorted

                // Find the mid element
                val n = sortedData.size
                if (n == 0)
                    0.0
                else if (n % 2 == 0) {
                    // Get the two elements and average them
                    val n2 = n / 2
                    val n1 = n2 - 1
                    (sortedData(n1) + sortedData(n2)) / 2
                } else
                    sortedData((n - 1) / 2)
            }
            case ("sum", field) => {
                data.foldLeft(0.0) { (sum, datum) => sum + fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(0.0) }
            }
            case ("max", field) => {
                data.foldLeft(Double.MinValue) { (max, datum) =>
                    val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(Double.MinValue)
                    if (v > max) v else max
                }
            }
            case ("min", field) => {
                data.foldLeft(Double.MaxValue) { (min, datum) =>
                    val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(Double.MaxValue)
                    if (v < min) v else min
                }
            }
            case ("count", field) => {
                data.count { datum => fieldParser(datum, field).isDefined }
            }
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