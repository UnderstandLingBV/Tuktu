package tuktu.api.Parsing

import fastparse.WhitespaceApi
import play.api.libs.json.{ Json, JsArray, JsObject, JsString, JsNull, JsValue }
import tuktu.api.utils.{ fieldParser, nearlyEqual }
import scala.util.Try
import tuktu.api.statistics.StatHelper
import fastparse.all.NoTrace

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
    val number: P[Double] = P(
        (
            // If we have a dot, we don't necessarily need a number before the dot
            ("-".? ~ CharIn('0' to '9').rep ~ "." ~ CharIn('0' to '9').rep(min = 1) |
                // Otherwise, we need a number
                "-".? ~ CharIn('0' to '9').rep(min = 1))
                ~ ("e" ~ "-".? ~ CharIn('0' to '9').rep(min = 1)).?).! map { _.toDouble })
    val parens: P[Double] = P("-".!.? ~ "(" ~/ addSub ~ ")").map { case (neg, double) => if (neg.isDefined) -double else double }
    val factor: P[Double] = P(parens | number)

    val pow: P[Double] = P(factor ~ (CharIn("^") ~/ factor).rep).map(evalPower)
    val divMul: P[Double] = P(pow ~ (CharIn("*/").! ~/ pow).rep).map(eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(eval)
    val expr: P[Double] = P(Start ~/ addSub ~ End)

    def evalPower(tree: (Double, Seq[Double])): Double = {
        def helper(list: List[Double]): Double = list match {
            case Nil       => 1
            case a :: tail => Math.pow(a, helper(tail))
        }
        helper(tree._1 :: tree._2.toList)
    }
    def eval(tree: (Double, Seq[(String, Double)])): Double = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "+" => left + right
                case "-" => left - right
                case "*" => left * right
                case "/" => left / right
            }
        }
    }

    def apply(str: String): Double = {
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
    val allowedFunctions = List("count", "avg", "median", "sum", "max", "min", "stdev")

    // Function parameter
    val parameter: P[String] = P("\"" ~ ("\\\"" | CharPred(_ != '"')).rep ~ "\"").!.map {
        str => Json.parse(str).as[String]
    }

    // All Tuktu-defined arithmetic functions
    val functions: P[Double] = P(
        StringIn(allowedFunctions: _*).! ~/ "(" ~/ (parameter | CharPred(_ != ')').rep.!) ~ ")").map {
            case ("avg", field) => {
                val (sum, count) = data.foldLeft(0.0, 0) {
                    case ((sum, count), datum) =>
                        val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }
                        (
                            sum + v.getOrElse(0.0),
                            count + { if (v.isDefined) 1 else 0 })
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
            case ("stdev", field) => {
                // Get variance
                val vars = StatHelper.getVariances(data, List(field))

                // Sqrt them to get StDevs
                vars.map(v => v._1 -> math.sqrt(v._2)).head._2
            }
            case ("count", field) => {
                data.count { datum => fieldParser(datum, field).isDefined }
            }
        }

    val parens: P[Double] = P("-".!.? ~ "(" ~/ addSub ~ ")").map { case (neg, double) => if (neg.isDefined) -double else double }
    val factor: P[Double] = P(parens | ArithmeticParser.number | functions)

    val pow: P[Double] = P(factor ~ (CharIn("^") ~/ factor).rep).map(ArithmeticParser.evalPower)
    val divMul: P[Double] = P(pow ~ (CharIn("*/").! ~/ pow).rep).map(ArithmeticParser.eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(ArithmeticParser.eval)
    val expr: P[Double] = P(Start ~/ addSub ~ End)

    def apply(str: String): Double = {
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
    def negate(tuple: (String, Boolean)): Boolean = {
        if (tuple._1.size % 2 == 0)
            tuple._2
        else
            !tuple._2
    }
    val literal: P[Boolean] = P("!".rep.! ~ ("true" | "false").!).map { case (neg, pred) => negate((neg, pred.toBoolean)) }

    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithExpr: P[Boolean] = P(ArithmeticParser.addSub ~ (">=" | "<=" | "==" | "!=" | "<" | ">").! ~ ArithmeticParser.addSub)
        .map {
            case (left, op, right) => op match {
                case "<"  => left < right && !nearlyEqual(left, right)
                case ">"  => left > right && !nearlyEqual(left, right)
                case "<=" => left < right || nearlyEqual(left, right)
                case ">=" => left > right || nearlyEqual(left, right)
                case "==" => nearlyEqual(left, right)
                case "!=" => !nearlyEqual(left, right)
            }
        }

    // Evaluate string expressions
    val strings: P[String] = P(
        CharIn(('a' to 'z') ++ ('A' to 'Z') ++ "_-+.,:;/\"'" ++ ('0' to '9')).rep.!)
    val stringExpr: P[Boolean] = P(strings ~ ("==" | "!=").! ~ strings)
        .map {
            case (left, "==", right) => left == right
            case (left, "!=", right) => left != right
        }

    val basePredicate: P[Boolean] = (literal | arithExpr | stringExpr)

    val equals: P[Boolean] = P(factor ~ (("==" | "!=").! ~ factor).rep(max = 1)).map(eval)
    val and: P[Boolean] = P(equals ~ ("&&".! ~/ equals).rep).map(eval)
    val or: P[Boolean] = P(and ~ ("||".! ~/ and).rep).map(eval)

    val parens: P[Boolean] = P("!".rep.! ~ "(" ~ or ~ ")").map(negate)
    val factor: P[Boolean] = P(parens | basePredicate)

    val expr: P[Boolean] = P(Start ~/ or ~ End)

    def eval(tree: (Boolean, Seq[(String, Boolean)])): Boolean = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "&&" => left && right
                case "||" => left || right
                case "==" => left == right
                case "!=" => left != right
            }
        }
    }

    def apply(str: String): Boolean = {
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

    // All Tuktu-defined functions
    val allowedFunctions: List[String] = List("containsFields", "isNumeric", "isNull", "isJSON", "containsSubstring", "isEmptyValue")
    val functions: P[Boolean] = P(((StringIn(allowedFunctions: _*).! ~ "(" ~/ CharPred(_ != ')').rep.! ~/ ")"))).map {
        case ("containsFields", fields) => fields.split(',').forall { path =>
            // Get the path and evaluate it against the datum
            fieldParser(datum, path).isDefined
        }
        case ("isNumeric", field) => Try {
            StatHelper.anyToDouble(fieldParser(datum, field).get)
        }.isSuccess
        case ("isNull", field) => fieldParser(datum, field) match {
            case Some(null)   => true
            case Some(JsNull) => true
            case _            => false
        }
        case ("isJSON", fields) => {
            fields.split(',').forall { path =>
                // Get the path, evaluate it against the datum, and check if it's JSON
                fieldParser(datum, path).flatMap { res =>
                    if (res.isInstanceOf[JsValue])
                        res.asInstanceOf[JsValue].asOpt[JsValue].map { _ => true }
                    else
                        None
                }.getOrElse(false)
            }
        }
        case ("containsSubstring", field) => {
            // Get the actual string and the substring
            val split = field.split(",")
            val string = split(0)
            val substring = split(1)
            string.contains(substring)
        }
        case ("isEmptyValue", field) => fieldParser(datum, field) match {
            case None => false
            case Some(value) => value match {
                case a: TraversableOnce[_] => a.isEmpty
                case a: String             => a.isEmpty
                case a: JsArray            => a.value.isEmpty
                case a: JsObject           => a.value.isEmpty
                case a: JsString           => a.value.isEmpty
                case a: Any                => a.toString.isEmpty
            }
        }
    }
    def allowedParameterfreeFunctions: List[String] = List("isEmpty")
    val parameterfreeFunctions: P[Boolean] = P(StringIn(allowedParameterfreeFunctions: _*).! ~ "(" ~/ ")").map {
        case ("isEmpty") => datum.isEmpty
    }
    val allFunctions: P[Boolean] = P("!".rep.! ~ (functions | parameterfreeFunctions)).map(PredicateParser.negate)

    // Boolean base logic
    val basePredicate: P[Boolean] = (allFunctions | PredicateParser.literal | PredicateParser.arithExpr | PredicateParser.stringExpr)

    val equals: P[Boolean] = P(factor ~ (("==" | "!=").! ~ factor).rep(max = 1)).map(PredicateParser.eval)
    val and: P[Boolean] = P(equals ~ ("&&".! ~/ equals).rep).map(PredicateParser.eval)
    val or: P[Boolean] = P(and ~ ("||".! ~/ and).rep).map(PredicateParser.eval)

    val parens: P[Boolean] = P("!".rep.! ~ "(" ~ or ~ ")").map(PredicateParser.negate)
    val factor: P[Boolean] = P(parens | basePredicate)

    val expr: P[Boolean] = P(Start ~/ or ~ End)

    def apply(str: String): Boolean = {
        expr.parse(str).get.value
    }
}