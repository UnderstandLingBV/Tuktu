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
    val parens: P[DoubleNode] = P("-".!.? ~ "(" ~ addSub ~ ")")
        .map { case (neg, n) => if (neg.isDefined) NegateNode(n) else n }
    val factor: P[DoubleNode] = P(parens | number | functions)

    // List of allowed functions
    val allowedFunctions = List("count", "distinct", "avg", "median", "sum", "max", "min", "stdev")
    // All Tuktu-defined arithmetic functions
    val functions: P[FunctionLeaf] = P(StringIn(allowedFunctions: _*).! ~ "(" ~ PredicateParser.stringNode ~ ")")
        .map { case (func, param) => FunctionLeaf(func, PredicateParser.evaluateStringNode(param)) }

    // Operations
    val pow: P[PowNode] = P(factor ~ (CharIn("^") ~ factor).rep)
        .map { case (base, seq) => PowNode(base +: seq) }
    val divMul: P[MultNode] = P(pow ~ (CharIn("*/").! ~ pow).rep)
        .map { case (base, seq) => MultNode(base, seq) }
    val addSub: P[AddNode] = P(divMul ~ (CharIn("+-").! ~ divMul).rep)
        .map { case (base, seq) => AddNode(base, seq) }
    val expr: P[DoubleNode] = P(Start ~ addSub ~ End)

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
    abstract class ValueNode

    case class NullNode() extends ValueNode

    abstract class StringNode extends ValueNode
    case class StringFunction(function: String, parameter: StringNode) extends StringNode
    case class StringLeaf(s: String) extends StringNode

    abstract class NumberNode extends ValueNode
    case class ArithmeticFunction(function: String, parameters: StringNode) extends NumberNode
    case class ArithmeticLeaf(node: ArithmeticParser.DoubleNode) extends NumberNode

    abstract class BooleanNode extends ValueNode
    case class BooleanLeaf(b: Boolean) extends BooleanNode
    case class BooleanFunction(function: String, parameters: Seq[StringNode]) extends BooleanNode
    case class AndNode(children: Seq[BooleanNode]) extends BooleanNode
    case class OrNode(children: Seq[BooleanNode]) extends BooleanNode
    case class NegateNode(or: BooleanNode) extends BooleanNode
    case class ComparisonNode(node1: ValueNode, operator: String, node2: ValueNode) extends BooleanNode

    import fastparse.noApi._
    import ArithmeticParser.White._

    /**
     *  Null Nodes
     */
    val nullLiteral: P[NullNode] = P("null").map { _ => NullNode() }

    /**
     * String Nodes
     */
    // Function parameter
    val stringLeaf: P[StringLeaf] = P("\"" ~ ("\\\"" | CharPred(_ != '"')).rep ~ "\"").!
        .map { str => StringLeaf(Json.parse(str).as[String]) }
    val nonFunctionParameter: P[StringLeaf] = {
        val nullString: P[StringLeaf] = P("null").map { _ => StringLeaf(null) }
        P(nullString | stringLeaf)
    }
    val allowedStringFunctions: List[String] = List("toLowerCase", "toUpperCase")
    val stringFunctions: P[StringFunction] = {
        val nonFunctionStringFunctions: P[StringFunction] = P(StringIn(allowedStringFunctions: _*).! ~ "(" ~ nonFunctionParameter ~ ")")
            .map { case (function, parameter) => StringFunction(function, parameter) }
        P(StringIn(allowedStringFunctions: _*).! ~ "(" ~ (nonFunctionParameter | nonFunctionStringFunctions) ~ ")")
            .map { case (function, parameter) => StringFunction(function, parameter) }
    }
    val stringNode: P[StringNode] = {
        val stringNode: P[StringNode] = P(stringFunctions | stringLeaf)
        P(stringNode | ("(" ~ stringNode ~ ")"))
    }

    /**
     * Number Nodes
     */
    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithNode: P[NumberNode] = {
        val arithLeaf: P[ArithmeticLeaf] = ArithmeticParser.addSub.map { node => ArithmeticLeaf(node) }
        val allowedArithmeticFunctions: List[String] = List("size")
        val arithmeticFunctions: P[ArithmeticFunction] = P(StringIn(allowedArithmeticFunctions: _*).! ~ "(" ~ stringNode ~ ")")
            .map { case (function, parameter) => ArithmeticFunction(function, parameter) }
        val arithNode: P[NumberNode] = P(arithmeticFunctions | arithLeaf)
        P(arithNode | ("(" ~ arithNode ~ ")"))
    }

    /**
     * Boolean Nodes
     */
    val booleanLiteral: P[BooleanLeaf] = P("!".rep.! ~ ("true" | "false").!)
        .map { case (neg, pred) => if (neg.size % 2 == 0) BooleanLeaf(pred.toBoolean) else BooleanLeaf(!pred.toBoolean) }

    // Functions
    val allowedFunctions: List[String] = List("containsFields", "isNumeric", "isNull", "isJSON", "containsSubstring", "isEmptyValue")
    val functions: P[BooleanFunction] = P(((StringIn(allowedFunctions: _*).! ~ "(" ~ (stringNode ~ ("," ~ stringNode).rep) ~ ")")))
        .map { case (function, (head, tail)) => BooleanFunction(function, head +: tail) }

    val allowedParameterfreeFunctions: List[String] = List("isEmpty")
    val parameterfreeFunctions: P[BooleanFunction] = P(StringIn(allowedParameterfreeFunctions: _*).! ~ "(" ~ ")")
        .map { case function => BooleanFunction(function, Nil) }

    val allFunctions: P[BooleanNode] = P("!".rep.! ~ (functions | parameterfreeFunctions))
        .map { case (neg, node) => if (neg.size % 2 == 0) node else NegateNode(node) }

    // Bringing everything together
    val valueNode: P[ValueNode] = P(nullLiteral | stringNode | parens | booleanLiteral | allFunctions | arithNode)
    val comparison: P[ComparisonNode] = P(valueNode ~ StringIn("<", ">", "<=", ">=", "==", "!=").! ~ valueNode)
        .map { case (node1, op, node2) => ComparisonNode(node1, op, node2) }

    val and: P[BooleanNode] = P(factor ~ ("&&" ~ factor).rep).map {
        case (head, Nil)  => head
        case (head, tail) => AndNode(head +: tail)
    }
    val or: P[BooleanNode] = P(and ~ ("||" ~ and).rep).map {
        case (head, Nil)  => head
        case (head, tail) => OrNode(head +: tail)
    }

    val parens: P[BooleanNode] = P("!".rep.! ~ "(" ~ or ~ ")")
        .map { case (n, or) => if (n.size % 2 == 0) or else NegateNode(or) }
    val factor: P[BooleanNode] = P(comparison | parens | allFunctions | booleanLiteral)

    val expr: P[BooleanNode] = P(Start ~ or ~ End)

    def evaluateStringNode(s: StringNode): String = s match {
        case StringLeaf(s)                       => s
        case StringFunction("toLowerCase", node) => evaluateStringNode(node).toLowerCase
        case StringFunction("toUpperCase", node) => evaluateStringNode(node).toUpperCase
    }
    def evaluateNumberNode(n: NumberNode)(implicit datum: Map[String, Any]): Option[Double] = n match {
        case ArithmeticLeaf(n) => Some(ArithmeticParser.eval(n))
        case ArithmeticFunction("size", field) =>
            fieldParser(datum, evaluateStringNode(field)) map {
                _ match {
                    case a: TraversableOnce[_] => a.size
                    case a: String             => a.size
                    case a: JsArray            => a.value.size
                    case a: JsObject           => a.fields.size
                    case a: JsString           => a.value.size
                }
            }
    }
    def evaluateBooleanNode(b: BooleanNode)(implicit datum: Map[String, Any]): Boolean = b match {
        case BooleanLeaf(b: Boolean) => b
        case AndNode(seq)            => seq.forall { evaluateBooleanNode(_) }
        case OrNode(seq)             => seq.exists { evaluateBooleanNode(_) }
        case NegateNode(n)           => !evaluateBooleanNode(n)
        case ComparisonNode(NullNode(), op, NullNode()) =>
            op match {
                case "==" => true
                case "!=" => false
                case "<=" => true
                case ">=" => true
                case "<"  => false
                case ">"  => false
            }
        case ComparisonNode(b1: BooleanNode, op, b2: BooleanNode) =>
            val left: Boolean = evaluateBooleanNode(b1)
            val right: Boolean = evaluateBooleanNode(b2)
            op match {
                case "==" => left == right
                case "!=" => left != right
                case "<=" => left <= right
                case ">=" => left >= right
                case "<"  => left < right
                case ">"  => left > right
            }
        case ComparisonNode(n1: NumberNode, op, n2: NumberNode) =>
            (evaluateNumberNode(n1), evaluateNumberNode(n2)) match {
                case (Some(left), Some(right)) => op match {
                    case "<"  => left < right && !nearlyEqual(left, right)
                    case ">"  => left > right && !nearlyEqual(left, right)
                    case "<=" => left < right || nearlyEqual(left, right)
                    case ">=" => left > right || nearlyEqual(left, right)
                    case "==" => nearlyEqual(left, right)
                    case "!=" => !nearlyEqual(left, right)
                }
            }
        case ComparisonNode(s1: StringNode, op, s2: StringNode) =>
            def evaluate(n: StringNode): String = n match {
                case StringLeaf(s)                       => s
                case StringFunction("toLowerCase", node) => evaluate(node).toLowerCase
                case StringFunction("toUpperCase", node) => evaluate(node).toUpperCase
            }

            op match {
                case "<"  => evaluate(s1) < evaluate(s2)
                case ">"  => evaluate(s1) > evaluate(s2)
                case "<=" => evaluate(s1) <= evaluate(s2)
                case ">=" => evaluate(s1) >= evaluate(s2)
                case "==" => evaluate(s1) == evaluate(s2)
                case "!=" => evaluate(s1) != evaluate(s2)
            }
        case ComparisonNode(_, "!=", _) => true
        case ComparisonNode(_, _, _)    => false
        case BooleanFunction(function, nodes) =>
            val params = nodes.map { evaluateStringNode(_) }
            function match {
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

    def apply(str: String, datum: Map[String, Any]): Boolean =
        evaluateBooleanNode(expr.parse(str).get.value)(datum)
}