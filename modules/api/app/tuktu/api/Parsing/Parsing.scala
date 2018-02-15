package tuktu.api.Parsing

import play.api.libs.json._
import scala.util.Try
import tuktu.api.utils.{ AnyToJsValue, evaluateTuktuString, fieldParser, nearlyEqual }
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
    case class NumericFunction(function: String, parameter: DoubleNode) extends DoubleNode
    case class FunctionLeaf(function: String, parameter: PredicateParser.ValueNode) extends DoubleNode
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
            ~ (("e" | "E") ~ "-".? ~ CharIn('0' to '9').rep(min = 1)).?).!
        .map { s => DoubleLeaf(s.toDouble) }
    val parens: P[DoubleNode] = P("-".!.? ~ "(" ~ addSub ~ ")")
        .map { case (neg, n) => if (neg.isDefined) NegateNode(n) else n }
    val factor: P[DoubleNode] = P(parens | number | functions | numericFunctions)

    // List of allowed functions
    val allowedFunctions = List("count", "distinct", "avg", "median", "sum", "max", "min", "stdev")
    // All Tuktu-defined arithmetic functions
    val functions: P[FunctionLeaf] = P(StringIn(allowedFunctions: _*).! ~ "(" ~ PredicateParser.parameter ~ ")")
        .map { case (func, param) => FunctionLeaf(func, param) }

    // List of allowed numeric functions
    val allowedNumericFunctions = List("abs", "floor", "ceil", "round", "sqrt", "log", "exp", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh")
    val numericFunctions: P[NumericFunction] = P(StringIn(allowedNumericFunctions: _*).! ~ "(" ~ addSub ~ ")")
        .map { case (func, param) => NumericFunction(func, param) }

    // Operations
    val pow: P[DoubleNode] = P("-".!.? ~ factor ~ (CharIn("^") ~ factor).rep)
        .map {
            case (None, base, Nil)    => base
            case (Some(_), base, Nil) => NegateNode(base)
            case (None, base, seq)    => PowNode(base +: seq)
            case (Some(_), base, seq) => NegateNode(PowNode(base +: seq))
        }
    val divMul: P[DoubleNode] = P(pow ~ (CharIn("*/").! ~ pow).rep)
        .map {
            case (base, Nil) => base
            case (base, seq) => MultNode(base, seq)
        }
    val addSub: P[DoubleNode] = P(divMul ~ (CharIn("+-").! ~ divMul).rep)
        .map {
            case (base, Nil) => base
            case (base, seq) => AddNode(base, seq)
        }
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
        case NumericFunction(f, value) => f match {
            case "abs"   => math.abs(eval(value))
            case "floor" => math.floor(eval(value))
            case "ceil"  => math.ceil(eval(value))
            case "round" => math.round(eval(value))
            case "sqrt"  => math.sqrt(eval(value))
            case "log"   => math.log(eval(value))
            case "exp"   => math.exp(eval(value))
            case "sin"   => math.sin(eval(value))
            case "cos"   => math.cos(eval(value))
            case "tan"   => math.tan(eval(value))
            case "asin"  => math.asin(eval(value))
            case "acos"  => math.acos(eval(value))
            case "atan"  => math.atan(eval(value))
            case "sinh"  => math.sinh(eval(value))
            case "cosh"  => math.cosh(eval(value))
            case "tanh"  => math.tanh(eval(value))
        }
        case FunctionLeaf(f, value) =>
            val field = value.evaluate(data.headOption.getOrElse(Map.empty)).asInstanceOf[String]
            f match {
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
    abstract class ValueNode {
        def evaluate(implicit datum: Map[String, Any]): Any
    }

    case class NullNode() extends ValueNode {
        def evaluate(implicit datum: Map[String, Any]): Null = null
    }

    case class TuktuStringFunction(f: evaluateTuktuString.TuktuStringFunction) extends ValueNode {
        def evaluate(implicit datum: Map[String, Any]): Any = {
            AnyToJsValue(fieldParser(datum, f.evaluateKey(datum))) match {
                case js: JsBoolean => js.value
                case js: JsNumber  => js.value.toDouble
                case js: JsString  => js.value
                case js: JsObject  => js.value
                case js: JsArray   => js.value
                case JsNull        => null
            }
        }
    }

    abstract class StringNode extends ValueNode {
        def evaluate(implicit datum: Map[String, Any]): String
    }
    case class StringFunction(function: String, parameter: ValueNode) extends StringNode {
        def evaluate(implicit datum: Map[String, Any]): String = function match {
            case "toLowerCase" => parameter.evaluate.asInstanceOf[String].toLowerCase
            case "toUpperCase" => parameter.evaluate.asInstanceOf[String].toUpperCase
        }
    }
    case class StringLeaf(s: String) extends StringNode {
        def evaluate(implicit datum: Map[String, Any]): String = s
    }

    abstract class NumberNode extends ValueNode {
        def evaluate(implicit datum: Map[String, Any]): Double
    }
    case class ArithmeticFunction(function: String, parameters: ValueNode) extends NumberNode {
        def evaluate(implicit datum: Map[String, Any]): Double = {
            val field = parameters.evaluate.asInstanceOf[String]
            function match {
                case "size" =>
                    fieldParser(datum, field).get match {
                        case a: TraversableOnce[_] => a.size
                        case a: String             => a.size
                        case a: JsArray            => a.value.size
                        case a: JsObject           => a.fields.size
                        case a: JsString           => a.value.size
                    }
            }
        }
    }
    case class ArithmeticLeaf(node: ArithmeticParser.DoubleNode) extends NumberNode {
        def evaluate(implicit datum: Map[String, Any]): Double = ArithmeticParser.eval(node)
    }

    abstract class BooleanNode extends ValueNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean
    }
    case class BooleanLeaf(b: Boolean) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = b
    }
    case class BooleanFunction(function: String, parameters: Seq[ValueNode]) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = {
            val params = parameters.map { _.evaluate.asInstanceOf[String] }
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
    }
    case class AndNode(children: Seq[BooleanNode]) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = children.forall { _.evaluate }
    }
    case class OrNode(children: Seq[BooleanNode]) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = children.exists { _.evaluate }
    }
    case class NegateNode(or: BooleanNode) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = !or.evaluate
    }
    case class ComparisonNode(node1: ValueNode, operator: String, node2: ValueNode) extends BooleanNode {
        def evaluate(implicit datum: Map[String, Any]): Boolean = (node1.evaluate, node2.evaluate) match {
            case (null, null) =>
                operator match {
                    case "==" => true
                    case "!=" => false
                    case "<=" => true
                    case ">=" => true
                    case "<"  => false
                    case ">"  => false
                    case _    => false
                }
            case (left: Boolean, right: Boolean) =>
                operator match {
                    case "==" => left == right
                    case "!=" => left != right
                    case "<=" => left <= right
                    case ">=" => left >= right
                    case "<"  => left < right
                    case ">"  => left > right
                    case _    => false
                }
            case (left: Double, right: Double) =>
                operator match {
                    case "<"  => left < right && !nearlyEqual(left, right)
                    case ">"  => left > right && !nearlyEqual(left, right)
                    case "<=" => left < right || nearlyEqual(left, right)
                    case ">=" => left > right || nearlyEqual(left, right)
                    case "==" => nearlyEqual(left, right)
                    case "!=" => !nearlyEqual(left, right)
                    case _    => false
                }
            case (left: String, right: String) =>
                operator match {
                    case "<"  => left < right
                    case ">"  => left > right
                    case "<=" => left <= right
                    case ">=" => left >= right
                    case "==" => left == right
                    case "!=" => left != right
                    case "in" => right.contains(left)
                }
            case (left: String, right: Map[String, _]) =>
                operator match {
                    case "in" => right.contains(left)
                    case _    => false
                }
            case (left: String, right: Seq[JsValue]) =>
                operator match {
                    case "in" => right.contains(JsString(left))
                    case _    => false
                }
            case (left: Boolean, right: Seq[JsValue]) =>
                operator match {
                    case "in" => right.contains(JsBoolean(left))
                    case _    => false
                }
            case (left: Double, right: Seq[JsValue]) =>
                operator match {
                    case "in" => right.contains(JsNumber(left))
                    case _    => false
                }
            case (_, _) if operator == "!=" => true
            case (_, _)                     => false
        }
    }

    import fastparse.noApi._
    import ArithmeticParser.White._

    /**
     *  Null Nodes
     */
    val nullLiteral: P[NullNode] = P("null").map { _ => NullNode() }

    /**
     * Tuktu String Nodes
     */
    val tuktuString: P[TuktuStringFunction] = evaluateTuktuString.tuktuString.map { TuktuStringFunction(_) }

    /**
     * String Nodes
     */
    // Function parameter
    val stringLeaf: P[StringLeaf] = P("\"" ~ ("\\\"" | CharPred(_ != '"')).rep ~ "\"").!
        .map { str => StringLeaf(Json.parse(str).as[String]) }
    val allowedStringFunctions: List[String] = List("toLowerCase", "toUpperCase")
    val stringFunctions: P[StringFunction] = P((StringIn(allowedStringFunctions: _*).! ~ "(" ~ stringFunctions ~ ")") | (StringIn(allowedStringFunctions: _*).! ~ "(" ~ parameter ~ ")"))
        .map { case (function, parameter) => StringFunction(function, parameter) }
    val stringNode: P[StringNode] = P(stringFunctions | stringLeaf)

    /**
     * Number Nodes
     */
    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithNode: P[NumberNode] = {
        val arithLeaf: P[ArithmeticLeaf] = ArithmeticParser.addSub.map { node => ArithmeticLeaf(node) }
        val allowedArithmeticFunctions: List[String] = List("size")
        val arithmeticFunctions: P[ArithmeticFunction] = P(StringIn(allowedArithmeticFunctions: _*).! ~ "(" ~ parameter ~ ")")
            .map { case (function, parameter) => ArithmeticFunction(function, parameter) }
        P(arithmeticFunctions | arithLeaf)
    }

    /**
     * Boolean Nodes
     */
    val booleanLiteral: P[BooleanLeaf] = P("!".rep.! ~ ("true" | "false").!)
        .map { case (neg, pred) => if (neg.size % 2 == 0) BooleanLeaf(pred.toBoolean) else BooleanLeaf(!pred.toBoolean) }

    // Functions
    val allowedFunctions: List[String] = List("containsFields", "isNumeric", "isNull", "isJSON", "containsSubstring", "isEmptyValue")
    val functions: P[BooleanFunction] = P(((StringIn(allowedFunctions: _*).! ~ "(" ~ (parameter ~ ("," ~ parameter).rep) ~ ")")))
        .map { case (function, (head, tail)) => BooleanFunction(function, head +: tail) }

    val allowedParameterfreeFunctions: List[String] = List("isEmpty")
    val parameterfreeFunctions: P[BooleanFunction] = P(StringIn(allowedParameterfreeFunctions: _*).! ~ "(" ~ ")")
        .map { case function => BooleanFunction(function, Nil) }

    val allFunctions: P[BooleanNode] = P("!".rep.! ~ (functions | parameterfreeFunctions))
        .map { case (neg, node) => if (neg.size % 2 == 0) node else NegateNode(node) }

    // Bringing everything together
    val valueNode: P[ValueNode] = P(nullLiteral | tuktuString | stringNode | parens | booleanLiteral | allFunctions | arithNode | "(" ~ valueNode ~ ")")
    val comparison: P[ComparisonNode] = P(valueNode ~ StringIn("<", ">", "<=", ">=", "==", "!=", "in").! ~ valueNode)
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

    val parameter: P[ValueNode] = P(stringNode | tuktuString | nullLiteral | arithNode | or | "(" ~ parameter ~ ")")

    val expr: P[BooleanNode] = P(Start ~ or ~ End)

    def prepare(str: String): BooleanNode = expr.parse(str).get.value
    def apply(str: String, datum: Map[String, Any]): Boolean = prepare(str).evaluate(datum)
}