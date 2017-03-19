package tuktu.api.Parsing

import play.api.libs.json._
import scala.util.Try
import tuktu.api.utils.{ fieldParser, nearlyEqual }
import tuktu.api.statistics.StatHelper
import fastparse.WhitespaceApi
import fastparse.all.NoTrace

/**
 * Performs arithmetics over a string representation
 */
object ArithmeticParser {
    // Tree structure
    abstract class DoubleNode
    case class DoubleLeaf(d: Double) extends DoubleNode
    case class FunctionLeaf(function: String, parameter: String) extends DoubleNode
    case class AddNode(base: DoubleNode, children: Seq[(String, DoubleNode)]) extends DoubleNode
    case class MultNode(base: DoubleNode, children: Seq[(String, DoubleNode)]) extends DoubleNode
    case class PowNode(seq: Seq[DoubleNode]) extends DoubleNode
    case class NegateNode(base: DoubleNode) extends DoubleNode

    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace((" " | "\t" | "\n").rep)
    }
    import fastparse.noApi._
    import White._

    // Allow all sorts of numbers, negative and scientific notation
    val number: P[DoubleLeaf] = P(
        // If we have a dot, we don't necessarily need a number before the dot
        ("-".? ~ CharIn('0' to '9').rep ~ "." ~ CharIn('0' to '9').rep(min = 1) |
            // Otherwise, we need a number
            "-".? ~ CharIn('0' to '9').rep(min = 1))
            ~ ("e" ~ "-".? ~ CharIn('0' to '9').rep(min = 1)).?).!
        .map { s => DoubleLeaf(s.toDouble) }
    val parens: P[DoubleNode] = P("-".!.? ~ "(" ~/ addSub ~ ")")
        .map { case (neg, n) => if (neg.isDefined) NegateNode(n) else n }
    val factor: P[DoubleNode] = P(parens | number | functions)

    // List of allowed functions
    val allowedFunctions = List("count", "distinct", "avg", "median", "sum", "max", "min", "stdev")
    // Function parameter
    val string: P[String] = P("null" | ("\"" ~ ("\\\"" | CharPred(c => c != '"' && c != ',')).rep ~ "\"")).!
        .map {
            case "null" => null
            case str    => Json.parse(str).as[String]
        }
    // All Tuktu-defined arithmetic functions
    val functions: P[FunctionLeaf] = P(StringIn(allowedFunctions: _*).! ~/ "(" ~/ string ~ ")")
        .map { case (func, param) => FunctionLeaf(func, param) }

    // Operations
    val pow: P[PowNode] = P(factor ~ (CharIn("^") ~/ factor).rep)
        .map { case (base, seq) => PowNode(base +: seq) }
    val divMul: P[MultNode] = P(pow ~ (CharIn("*/").! ~/ pow).rep)
        .map { case (base, seq) => MultNode(base, seq) }
    val addSub: P[AddNode] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep)
        .map { case (base, seq) => AddNode(base, seq) }
    val expr: P[DoubleNode] = P(Start ~/ addSub ~ End)

    // Evaluate the tree
    def eval(d: DoubleNode)(implicit data: List[Map[String, Any]] = Nil): Double = d match {
        case DoubleLeaf(d) => d
        case AddNode(base, ops) =>
            ops.foldLeft(eval(base)) {
                case (acc, (op, current)) => op match {
                    case "+" => acc + eval(current)
                    case "-" => acc - eval(current)
                }
            }
        case MultNode(base, ops) =>
            ops.foldLeft(eval(base)) {
                case (acc, (op, current)) => op match {
                    case "*" => acc * eval(current)
                    case "/" => acc / eval(current)
                }
            }
        case PowNode(seq) =>
            seq.foldRight(1d) {
                case (current, acc) => Math.pow(eval(current), acc)
            }
        case NegateNode(n) => -eval(n)
        case FunctionLeaf(f, field) => f match {
            case "avg" =>
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
            case "median" =>
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
            case "sum" =>
                data.foldLeft(0.0) { (sum, datum) => sum + fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(0.0) }
            case "max" =>
                data.foldLeft(Double.MinValue) { (max, datum) =>
                    val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(Double.MinValue)
                    if (v > max) v else max
                }
            case "min" =>
                data.foldLeft(Double.MaxValue) { (min, datum) =>
                    val v = fieldParser(datum, field).map { StatHelper.anyToDouble(_) }.getOrElse(Double.MaxValue)
                    if (v < min) v else min
                }
            case "stdev" =>
                // Get variance
                val vars = StatHelper.getVariances(data, List(field))

                // Sqrt them to get StDevs
                vars.map(v => v._1 -> math.sqrt(v._2)).head._2
            case "count" =>
                data.count { datum => fieldParser(datum, field).isDefined }
            case "distinct" =>
                data.map { datum => fieldParser(datum, field) }.filter { _.isDefined }.distinct.size
        }
    }

    def apply(str: String, data: List[Map[String, Any]] = Nil): Double = eval(expr.parse(str).get.value)(data)
}

/**
 * Parses Boolean predicates
 */
object PredicateParser {
    // Tree structure
    abstract class BooleanNode
    case class BooleanLeaf(b: Boolean) extends BooleanNode
    case class ArithmeticFunctionLeaf(function: String, parameters: Seq[String])
    case class ArithmeticLeaf(left: Either[ArithmeticParser.DoubleNode, ArithmeticFunctionLeaf], op: String, right: Either[ArithmeticParser.DoubleNode, ArithmeticFunctionLeaf]) extends BooleanNode
    case class FunctionLeaf(function: String, parameters: Seq[String]) extends BooleanNode
    case class EqualsNode(node1: BooleanNode, operator: String, b2: BooleanNode) extends BooleanNode
    case class AndNode(children: Seq[BooleanNode]) extends BooleanNode
    case class OrNode(children: Seq[BooleanNode]) extends BooleanNode
    case class NegateNode(or: BooleanNode) extends BooleanNode

    import fastparse.noApi._
    import ArithmeticParser.White._

    // Boolean literals
    val literal: P[BooleanLeaf] = P("!".rep.! ~ ("true" | "false").!)
        .map { case (neg, pred) => if (neg.size % 2 == 0) BooleanLeaf(pred.toBoolean) else BooleanLeaf(!pred.toBoolean) }

    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val allowedArithmeticFunctions: List[String] = List("size")
    val arithmeticFunctions: P[ArithmeticFunctionLeaf] = P(((StringIn(allowedArithmeticFunctions: _*).! ~ "(" ~/ (ArithmeticParser.string ~/ ("," ~ ArithmeticParser.string).rep) ~/ ")")))
        .map { case (function, (head, tail)) => ArithmeticFunctionLeaf(function, tail.+:(head)) }
    val arithNode: P[Either[ArithmeticParser.AddNode, ArithmeticFunctionLeaf]] = P((ArithmeticParser.addSub | arithmeticFunctions)).map {
        case n: ArithmeticParser.AddNode => Left(n)
        case n: ArithmeticFunctionLeaf   => Right(n)
    }
    val arithExpr: P[ArithmeticLeaf] = P(arithNode ~ (">=" | "<=" | "==" | "!=" | "<" | ">").! ~ arithNode)
        .map { case (left, op, right) => ArithmeticLeaf(left, op, right) }

    // Evaluate string expressions
    val stringExpr: P[BooleanLeaf] = P(ArithmeticParser.string ~ ("==" | "!=").! ~ ArithmeticParser.string)
        .map {
            case (left, "==", right) => left == right
            case (left, "!=", right) => left != right
        }.map { BooleanLeaf(_) }

    // Functions
    val allowedFunctions: List[String] = List("containsFields", "isNumeric", "isNull", "isJSON", "containsSubstring", "isEmptyValue")
    val functions: P[FunctionLeaf] = P(((StringIn(allowedFunctions: _*).! ~ "(" ~/ (ArithmeticParser.string ~/ ("," ~ ArithmeticParser.string).rep) ~/ ")")))
        .map { case (function, (head, tail)) => FunctionLeaf(function, tail.+:(head)) }

    val allowedParameterfreeFunctions: List[String] = List("isEmpty")
    val parameterfreeFunctions: P[FunctionLeaf] = P(StringIn(allowedParameterfreeFunctions: _*).! ~ "(" ~/ ")")
        .map { case function => FunctionLeaf(function, Nil) }

    val allFunctions: P[BooleanNode] = P("!".rep.! ~ (functions | parameterfreeFunctions))
        .map { case (n, f) => if (n.size % 2 == 0) f else NegateNode(f) }

    // Bringing everything together
    val basePredicate: P[BooleanNode] = (allFunctions | literal | arithExpr | stringExpr)

    val equals: P[BooleanNode] = P(factor ~ (("==" | "!=").! ~ factor).rep(max = 1))
        .map { case (head, tail) => if (tail.isEmpty) head else EqualsNode(head, tail.head._1, tail.head._2) }
    val and: P[AndNode] = P(equals ~ ("&&" ~/ equals).rep)
        .map { case (head, tail) => AndNode(head +: tail) }
    val or: P[OrNode] = P(and ~ ("||" ~/ and).rep)
        .map { case (head, tail) => OrNode(head +: tail) }

    val parens: P[BooleanNode] = P("!".rep.! ~ "(" ~ or ~ ")")
        .map { case (n, or) => if (n.size % 2 == 0) or else NegateNode(or) }
    val factor: P[BooleanNode] = P(parens | basePredicate)

    val expr: P[BooleanNode] = P(Start ~/ or ~ End)

    def apply(str: String, datum: Map[String, Any] = Map.empty): Boolean = {
        def eval(b: BooleanNode): Boolean = b match {
            case BooleanLeaf(b: Boolean)  => b
            case EqualsNode(n1, "==", n2) => eval(n1) == eval(n2)
            case EqualsNode(n1, "!=", n2) => eval(n1) != eval(n2)
            case AndNode(seq)             => seq.forall { eval(_) }
            case OrNode(seq)              => seq.exists { eval(_) }
            case NegateNode(n)            => !eval(n)
            case ArithmeticLeaf(left, op, right) =>
                def evaluate(n: ArithmeticFunctionLeaf): Option[Double] = {
                    n.function match {
                        case "size" =>
                            fieldParser(datum, n.parameters.head) match {
                                case None => None
                                case Some(value) => value match {
                                    case a: TraversableOnce[_] => Some(a.size)
                                    case a: String             => Some(a.size)
                                    case a: JsArray            => Some(a.value.size)
                                    case a: JsObject           => Some(a.value.size)
                                    case a: JsString           => Some(a.value.size)
                                    case a: Any                => None
                                }
                            }
                    }
                }
                val l = left match {
                    case Left(n)  => Some(ArithmeticParser.eval(n))
                    case Right(n) => evaluate(n)
                }
                val r = right match {
                    case Left(n)  => Some(ArithmeticParser.eval(n))
                    case Right(n) => evaluate(n)
                }
                (l, r) match {
                    case (Some(l), Some(r)) => op match {
                        case "<"  => l < r && !nearlyEqual(l, r)
                        case ">"  => l > r && !nearlyEqual(l, r)
                        case "<=" => l < r || nearlyEqual(l, r)
                        case ">=" => l > r || nearlyEqual(l, r)
                        case "==" => nearlyEqual(l, r)
                        case "!=" => !nearlyEqual(l, r)
                    }
                    case _ => false
                }
            case FunctionLeaf(f, params) => f match {
                case "containsFields" => params.forall { path =>
                    // Get the path and evaluate it against the datum
                    fieldParser(datum, path).isDefined
                }
                case "isNumeric" => params.forall { path =>
                    Try {
                        StatHelper.anyToDouble(fieldParser(datum, path).get)
                    }.isSuccess
                }
                case "isNull" => params.forall { path =>
                    fieldParser(datum, path) match {
                        case Some(null)   => true
                        case Some(JsNull) => true
                        case _            => false
                    }
                }
                case "isJSON" => params.forall { path =>
                    // Get the path, evaluate it against the datum, and check if it's JSON
                    fieldParser(datum, path).flatMap { res =>
                        if (res.isInstanceOf[JsValue])
                            res.asInstanceOf[JsValue].asOpt[JsValue].map { _ => true }
                        else
                            None
                    }.getOrElse(false)
                }
                case "containsSubstring" =>
                    // Get the actual string and the substring
                    params.tail.forall { substring =>
                        params.head.contains(substring)
                    }
                case "isEmptyValue" => params.forall { path =>
                    fieldParser(datum, path) match {
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
                case "isEmpty" => datum.isEmpty
            }
        }
        eval(expr.parse(str).get.value)
    }
}