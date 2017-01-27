package tuktu.test.api

import play.api.libs.json._
import tuktu.api.Parsing._
import scala.util.Random
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
    }

    /**
     * TuktuArithmeticsParser
     */

    "TuktuArithmeticsParser" should {
        "return correct results for its functions on random data" in {
            val random = for (i <- 0 to Random.nextInt(50)) yield (Random.nextDouble - Random.nextDouble) * (Random.nextInt(100) + 1)
            val dp = random.map { n => Map("a" -> n) }.toList
            val parser = new TuktuArithmeticsParser(dp)
            parser("17 + min(a)") should be(17 + random.min)
            parser("17 + max(a)") should be(17 + random.max)
            parser("(max(a) - min(a)) / 2") should be((random.max - random.min) / 2)
            parser("sum(a) * 1.7e1") should be(random.sum * 1.7e1)
            parser("(avg(a) - 2) * 17") should be((random.sum / random.size - 2) * 17)
            parser("((count(a)) * 2)") should be(random.size * 2)
            parser("median(a)") should be {
                val sorted = random.sorted
                val n = sorted.size
                if (n % 2 == 0)
                    (sorted(n / 2) + sorted(n / 2 - 1)) / 2
                else
                    sorted((n - 1) / 2)
            }
            parser("stdev(a)") should be {
                val mean = random.sum / random.size
                math.sqrt(random.map(n => math.pow(n - mean, 2)).sum / random.size)
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
            PredicateParser("AbS == AbS", datum) should be(true)
            PredicateParser("AbS != Abs", datum) should be(true)
            PredicateParser("!(ABS != abs)", datum) should be(false)
        }

        "support operator priority" in {
            PredicateParser("true || false && false", datum) should be(true)
            PredicateParser("(true || false) && false", datum) should be(false)
            PredicateParser("(true && false == false)", datum) should be(true)
        }

        "support different comparisons" in {
            PredicateParser("false || true && 1.7e1 != 17 || ABS == abs", datum) should be(false)
            PredicateParser("!false && !(true && 1.7e1 != 17) && !(ABS == abs)", datum) should be(true)
        }

        "support nested brackets" in {
            PredicateParser("((asd == asd) && (false == false) == true)", datum) should be(true)
        }

        "return correct results for its functions" in {
            // isNull
            PredicateParser("isNull(null.1)", datum) should be(true)
            PredicateParser("isNull(null.2)", datum) should be(true)
            PredicateParser("isNull(Int)", datum) should be(false)
            PredicateParser("isNull(asd)", datum) should be(false)

            // isNumeric
            PredicateParser("isNumeric(JsNumber)", datum) should be(true)
            PredicateParser("isNumeric(Double)", datum) should be(true)
            PredicateParser("isNumeric(Int)", datum) should be(true)
            PredicateParser("isNumeric(String)", datum) should be(false)
            PredicateParser("isNumeric(asd)", datum) should be(false)

            // isJSON
            PredicateParser("isJSON(null.2)", datum) should be(true)
            PredicateParser("isJSON(JsNumber)", datum) should be(true)
            PredicateParser("isJSON(JsObject.a.b)", datum) should be(true)
            PredicateParser("isJSON(JsObject.a.asd)", datum) should be(false)
            PredicateParser("isJSON(JsArray)", datum) should be(true)
            PredicateParser("isJSON(empty3)", datum) should be(true)
            PredicateParser("isJSON(asd)", datum) should be(false)
            PredicateParser("isJSON(Int)", datum) should be(false)
            PredicateParser("isJSON(String)", datum) should be(false)
            PredicateParser("isJSON(null.2,JsObject.a.b)", datum) should be(true)

            // containsFields
            PredicateParser("containsFields(null.1,JsObject.a.b,empty6)", datum) should be(true)
            PredicateParser("containsFields(null.1,asd)", datum) should be(false)
            PredicateParser("containsFields(" + datum.keys.mkString(",") + ")", datum) should be(true)
            PredicateParser("containsFields(" + datum.keys.mkString(",") + ",asd)", datum) should be(false)

            // containsSubstring
            PredicateParser("containsSubstring(myString,string)", datum) should be(false)
            PredicateParser("containsSubstring(myString,String)", datum) should be(true)
            PredicateParser("containsSubstring(String,myString)", datum) should be(false)

            // isEmptyValue
            PredicateParser("isEmptyValue(empty1)", datum) should be(true)
            PredicateParser("isEmptyValue(empty2)", datum) should be(true)
            PredicateParser("isEmptyValue(empty3)", datum) should be(true)
            PredicateParser("isEmptyValue(empty4)", datum) should be(true)
            PredicateParser("isEmptyValue(empty5)", datum) should be(true)
            PredicateParser("isEmptyValue(empty6)", datum) should be(true)
            PredicateParser("isEmptyValue(asd)", datum) should be(false)
            PredicateParser("isEmptyValue(JsObject)", datum) should be(false)
            PredicateParser("isEmptyValue(JsArray)", datum) should be(false)
            PredicateParser("isEmptyValue(String)", datum) should be(false)

            // isEmpty
            PredicateParser("isEmpty()", datum) should be(false)
            PredicateParser("isEmpty()", Map()) should be(true)
        }
    }
}