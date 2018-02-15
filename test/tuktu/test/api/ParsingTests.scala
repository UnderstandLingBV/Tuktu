package tuktu.test.api

import play.api.libs.json._
import tuktu.api.Parsing._
import scala.util.{ Random, Try }
import org.scalatest._
import org.scalatestplus.play.PlaySpec
import Matchers._

@DoNotDiscover
class ParsingTests extends PlaySpec {

    /**
     * ArithmeticParser
     */

    "ArithmeticParser" should {
        "parse numbers" in {
            ArithmeticParser("1e3") should be(1e3)
            ArithmeticParser("17.3") should be(17.3)
            ArithmeticParser("1.7e2") should be(1.7e2)
            ArithmeticParser(".7e2") should be(.7e2)
            ArithmeticParser("-1e3") should be(-1e3)
            ArithmeticParser("-17.3") should be(-17.3)
            ArithmeticParser("-1.7e-2") should be(-1.7e-2)
            ArithmeticParser("-1.7E-2") should be(-1.7E-2)
            ArithmeticParser("-.7e-2") should be(-.7e-2)
        }

        "do some basic arithmetic" in {
            ArithmeticParser("17 + 23 + 18") should be(17 + 23 + 18)
            ArithmeticParser("1.7e2 - 1.8e1 + .9e0") should be(1.7e2 - 1.8e1 + .9e0)
            ArithmeticParser("1.7e2 * 1.8e1 * 17") should be(1.7e2 * 1.8e1 * 17)
            ArithmeticParser("1.7e2 / 1.8e1 / 17") should be(1.7e2 / 1.8e1 / 17)
        }

        "honor operation order" in {
            ArithmeticParser("1.7e2 - 1.8e1 * 2 + 12") should be(1.7e2 - 1.8e1 * 2 + 12)
            ArithmeticParser("1.7e2 - 1.8e1 / 2 - 12") should be(1.7e2 - 1.8e1 / 2 - 12)
            ArithmeticParser("3 + 3 * 3 ^ 3") should be(3 + 3 * Math.pow(3, 3))
            ArithmeticParser("2 + 2 * 2 ^ 2 ^ 3") should be(514)
            ArithmeticParser("-2^3") should be(-8)
        }

        "honor bracket order" in {
            ArithmeticParser("17 + (1.7e2 - 1.8e1) * (2 + 7) - 23") should be(17 + (1.7e2 - 1.8e1) * (2 + 7) - 23)
            ArithmeticParser("17 + (1.7e2 - 1.8e1) / (2 + 7) - 23") should be(17 + (1.7e2 - 1.8e1) / (2 + 7) - 23)
        }

        "support nested brackets" in {
            ArithmeticParser("((17)) + ((1.7e2 - 1.8e1) * (2 + 7) - (23))") should be(((17)) + ((1.7e2 - 1.8e1) * (2 + 7) - (23)))
        }

        "ignore white space" in {
            ArithmeticParser("  1e3  ") should be(1e3)
            ArithmeticParser(" 17 + ( 1.7e2 - 1.8e1 ) * ( 2 + 7 ) - 23 ") should be(17 + (1.7e2 - 1.8e1) * (2 + 7) - 23)
            ArithmeticParser(" 17 + ( 1.7e2 - 1.8e1 ) / ( 2 + 7 ) - 23 ") should be(17 + (1.7e2 - 1.8e1) / (2 + 7) - 23)
        }

        "support basic arithmetic functions" in {
            ArithmeticParser(" abs ( -0.5 ) ") should be(math.abs(-0.5))
            ArithmeticParser(" floor ( -0.5 ) ") should be(math.floor(-0.5))
            ArithmeticParser(" ceil ( -0.5 ) ") should be(math.ceil(-0.5))
            ArithmeticParser(" round ( -0.5 ) ") should be(math.round(-0.5))
            ArithmeticParser(" sqrt ( abs( -0.5 ) ) ") should be(math.sqrt(math.abs(-0.5)))
            ArithmeticParser(" log ( abs ( -0.5 ) ) ") should be(math.log(math.abs(-0.5)))
            ArithmeticParser(" exp ( -0.5 ) ") should be(math.exp(-0.5))
            ArithmeticParser(" sin ( -0.5 ) ") should be(math.sin(-0.5))
            ArithmeticParser(" cos ( -0.5 ) ") should be(math.cos(-0.5))
            ArithmeticParser(" tan ( -0.5 ) ") should be(math.tan(-0.5))
            ArithmeticParser(" asin ( -0.5 ) ") should be(math.asin(-0.5))
            ArithmeticParser(" acos ( -0.5 ) ") should be(math.acos(-0.5))
            ArithmeticParser(" atan ( -0.5 ) ") should be(math.atan(-0.5))
            ArithmeticParser(" sinh ( -0.5 ) ") should be(math.sinh(-0.5))
            ArithmeticParser(" cosh ( -0.5 ) ") should be(math.cosh(-0.5))
            ArithmeticParser(" tanh ( -0.5 ) ") should be(math.tanh(-0.5))

            ArithmeticParser(" exp ( - (1 - 2) ^ 2 ) ") should be(math.exp(-math.pow(1 - 2, 2)))
        }

        "return correct results for its functions on random data" in {
            val random = for (i <- 0 to Random.nextInt(50)) yield (Random.nextDouble - Random.nextDouble) * (Random.nextInt(100) + 1)
            val dp = random.map { n => Map("a" -> n) }.toList
            ArithmeticParser("17 + min(\"a\")", dp) should be(17 + random.min)
            ArithmeticParser("17 + max(\"a\")", dp) should be(17 + random.max)
            ArithmeticParser("(max(\"a\") - min(\"a\")) / 2", dp) should be((random.max - random.min) / 2)
            ArithmeticParser("sum(\"a\") * 1.7e1", dp) should be(random.sum * 1.7e1)
            ArithmeticParser("(avg(\"a\") - 2) * 17", dp) should be((random.sum / random.size - 2) * 17)
            ArithmeticParser("((count(\"a\")) * 2)", dp) should be(random.size * 2)
            ArithmeticParser("median(\"a\")", dp) should be {
                val sorted = random.sorted
                val n = sorted.size
                if (n % 2 == 0)
                    (sorted(n / 2) + sorted(n / 2 - 1)) / 2
                else
                    sorted((n - 1) / 2)
            }
            ArithmeticParser("stdev(\"a\")", dp) should be {
                val mean = random.sum / random.size
                math.sqrt(random.map(n => math.pow(n - mean, 2)).sum / random.size)
            }

            val dp2 = (for (i <- 0 to Random.nextInt(100)) yield Map((if (i % 3 == 0) "b" else "a") -> Random.nextInt(100))).toList
            ArithmeticParser("distinct(\"a\")", dp2) should be {
                dp2.filter { _.contains("a") }.map { _.apply("a") }.distinct.size
            }
        }
    }

    /**
     * PredicateParser
     */

    "PredicateParser" should {
        val datum = Map(
            "null" -> Map("1" -> null, "2" -> JsNull),
            "JsNumber" -> JsNumber(-17.3e-1),
            "Double" -> 1.723e3,
            "Int" -> -182,
            "String" -> "myString",
            "Substring1" -> "String",
            "Substring2" -> "string",
            "JsObject" -> Json.obj("a" -> Json.obj("b" -> "c")),
            "JsArray" -> Json.arr(1, "2", List(3)),
            "empty1" -> Json.obj(),
            "empty2" -> Json.arr(),
            "empty3" -> JsString(""),
            "empty4" -> "",
            "empty5" -> List(),
            "empty6" -> Map())

        "evaluate literal expressions correctly" in {
            PredicateParser("true", datum) should be(true)
            PredicateParser("false", datum) should be(false)
            PredicateParser("!true", datum) should be(false)
            PredicateParser("!false", datum) should be(true)
            PredicateParser("!(true)", datum) should be(false)
            PredicateParser("!(false)", datum) should be(true)
        }

        "support multiple negation of literals" in {
            PredicateParser("!(!(true))", datum) should be(true)
            PredicateParser("!(!true)", datum) should be(true)
            PredicateParser("!!true", datum) should be(true)
            PredicateParser("!!!(!!(!!true))", datum) should be(false)
            PredicateParser("!(!(false))", datum) should be(false)
            PredicateParser("!(!false)", datum) should be(false)
            PredicateParser("!!false", datum) should be(false)
            PredicateParser("!!!(!!!(!!!false))", datum) should be(true)
        }

        "support basic predicates" in {
            PredicateParser("true && false", datum) should be(false)
            PredicateParser("true && !false", datum) should be(true)
            PredicateParser("!true || !true", datum) should be(false)
            PredicateParser("!!true || false", datum) should be(true)
        }

        "support number comparisons" in {
            PredicateParser("1.7e1 == 17", datum) should be(true)
            PredicateParser("(1.7e1) == (17)", datum) should be(true)
            PredicateParser("!((1.7e1) == (17))", datum) should be(false)
            PredicateParser(".7e1 > -.0", datum) should be(true)
            PredicateParser(".0 == -0", datum) should be(true)
            PredicateParser(".2 + .1 == .3", datum) should be(true)
            PredicateParser(".2 + .1 <= .3", datum) should be(true)
            PredicateParser(".2 + .1 >= .3", datum) should be(true)
            PredicateParser(".2 + .1 > .3", datum) should be(false)
            PredicateParser(".2 + .1 < .3", datum) should be(false)
            PredicateParser(".2 + .1 != .3", datum) should be(false)
            PredicateParser("-.1 / .3 + 0.333333333333333333 == .1 / -.3 + 0.333333333333333333", datum) should be(true)
        }

        "support string comparisons" in {
            PredicateParser("\"AbS\" == \"AbS\"", datum) should be(true)
            PredicateParser("\"AbS\" != \"Abs\"", datum) should be(true)
            PredicateParser("!(\"ABS\" != \"abs\")", datum) should be(false)
        }

        "support null" in {
            PredicateParser("\"ABC\" == null", datum) should be(false)
            PredicateParser("null != null", datum) should be(false)
            Try { PredicateParser.expr.parse("containsSubstring(null, null, null)") }.isSuccess should be(true)
            Try { PredicateParser.expr.parse("size(null) == 17") }.isSuccess should be(true)
            Try { PredicateParser.expr.parse("isNull(toLowerCase(null))") }.isSuccess should be(true)
        }

        "support operator priority" in {
            PredicateParser("true || false && false", datum) should be(true)
            PredicateParser("(true || false) && false", datum) should be(false)
            PredicateParser("(true && false == false)", datum) should be(true)
        }

        "support different comparisons" in {
            PredicateParser("false || true && 1.7e1 != 17 || \"ABS\" == \"abs\"", datum) should be(false)
            PredicateParser("!false && !(true && 1.7e1 != 17) && !(\"ABS\" == \"abs\")", datum) should be(true)
        }

        "support nested brackets" in {
            PredicateParser("((\"asd\" == \"asd\") && (false == false) == true)", datum) should be(true)
        }

        "support string functions" in {
            PredicateParser("toUpperCase(\"abc\") == \"ABC\"", datum) should be(true)
            PredicateParser("toLowerCase(\"ABC\") == \"abc\"", datum) should be(true)
            PredicateParser("toLowerCase(toLowerCase(toUpperCase(\"AbC\"))) == \"abc\"", datum) should be(true)
        }

        "support arithmetic functions" in {
            PredicateParser("size(\"null\") > 0", datum) should be(true)
            PredicateParser("size(\"null\") <= 2", datum) should be(true)
            PredicateParser("size(\"null\") != 2", datum) should be(false)
            PredicateParser("size(\"JsObject\") == 1", datum) should be(true)
            PredicateParser("size(\"JsArray\") == 3", datum) should be(true)
            PredicateParser("size(\"String\") == 8", datum) should be(true)
        }

        "support boolean functions" in {
            // isNull
            PredicateParser("isNull(\"null.1\")", datum) should be(true)
            PredicateParser("isNull(\"null.2\")", datum) should be(true)
            PredicateParser("isNull(\"Int\")", datum) should be(false)
            PredicateParser("isNull(\"asd\")", datum) should be(false)

            // isNumeric
            PredicateParser("isNumeric(\"JsNumber\")", datum) should be(true)
            PredicateParser("isNumeric(\"Double\")", datum) should be(true)
            PredicateParser("isNumeric(\"Int\")", datum) should be(true)
            PredicateParser("isNumeric(\"String\")", datum) should be(false)
            PredicateParser("isNumeric(\"asd\")", datum) should be(false)

            // isJSON
            PredicateParser("isJSON(\"null.2\")", datum) should be(true)
            PredicateParser("isJSON(\"JsNumber\")", datum) should be(true)
            PredicateParser("isJSON(\"JsObject.a.b\")", datum) should be(true)
            PredicateParser("isJSON(\"JsObject.a.asd\")", datum) should be(false)
            PredicateParser("isJSON(\"JsArray\")", datum) should be(true)
            PredicateParser("isJSON(\"empty3\")", datum) should be(true)
            PredicateParser("isJSON(\"asd\")", datum) should be(false)
            PredicateParser("isJSON(\"Int\")", datum) should be(false)
            PredicateParser("isJSON(\"String\")", datum) should be(false)
            PredicateParser("isJSON(\"null.2\", \"JsObject.a.b\")", datum) should be(true)

            // containsFields
            PredicateParser("containsFields(\"null.1\", \"JsObject.a.b\", \"empty6\")", datum) should be(true)
            PredicateParser("containsFields(\"null.1\" , \"asd\")", datum) should be(false)
            PredicateParser("containsFields(" + datum.keys.map { JsString(_) }.mkString(", ") + ")", datum) should be(true)
            PredicateParser("containsFields(" + datum.keys.map { JsString(_) }.mkString(", ") + ", \"asd\")", datum) should be(false)

            // containsSubstring
            PredicateParser("containsSubstring(\"myString\", \"string\")", datum) should be(false)
            PredicateParser("containsSubstring(toLowerCase(\"myString\"), toLowerCase(\"strinG\"))", datum) should be(true)
            PredicateParser("containsSubstring(\"myString\", \"String\")", datum) should be(true)
            PredicateParser("containsSubstring(\"String\", \"myString\")", datum) should be(false)

            // isEmptyValue
            PredicateParser("isEmptyValue(\"empty1\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"empty2\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"empty3\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"empty4\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"empty5\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"empty6\")", datum) should be(true)
            PredicateParser("isEmptyValue(\"asd\")", datum) should be(false)
            PredicateParser("isEmptyValue(\"JsObject\")", datum) should be(false)
            PredicateParser("isEmptyValue(\"JsArray\")", datum) should be(false)
            PredicateParser("isEmptyValue(\"String\")", datum) should be(false)

            // isEmpty
            PredicateParser("isEmpty()", datum) should be(false)
            PredicateParser("isEmpty()", Map()) should be(true)
        }

        "support Tuktu Strings" in {
            PredicateParser("${null.1} == ((null)) && (${null.2}) == null", datum) should be(true)
            PredicateParser("${JsNumber} == -17.3e-1 && ((${Double}) == ((1.723e3))) && ${Int} == -182", datum) should be(true)
            PredicateParser("toLowerCase(${Substring1}) == toLowerCase(${Substring2})", datum) should be(true)
            PredicateParser("${empty3} == ${empty4}", datum) should be(true)
            PredicateParser("containsSubstring(toLowerCase((${String})), toLowerCase(${Substring1}), toLowerCase(${Substring2}))", datum) should be(true)
        }

        "support 'in' comparison" in {
            PredicateParser("\"1\" in ${null} && \"2\" in ${null}", datum) should be(true)
            PredicateParser("\"3\" in ${null}", datum) should be(false)
            PredicateParser("1 in ${JsArray} && \"2\" in ${JsArray}", datum) should be(true)
            PredicateParser("\"3\" in ${JsArray}", datum) should be(false)
            PredicateParser("${Substring1} in ${String}", datum) should be(true)
            PredicateParser("${Substring2} in ${String}", datum) should be(false)
        }
    }
}