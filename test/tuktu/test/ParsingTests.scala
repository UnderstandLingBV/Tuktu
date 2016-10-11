package tuktu.test

import play.api.libs.json._
import tuktu.utils.{ ArithmeticParser, TuktuArithmeticsParser, PredicateParser, TuktuPredicateParser }
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
        "evaluate literal expressions correctly" in {
            PredicateParser("true") should be(true)
            PredicateParser("false") should be(false)
            PredicateParser("!true") should be(false)
            PredicateParser("!false") should be(true)
            PredicateParser("!(true)") should be(false)
            PredicateParser("!(false)") should be(true)
        }

        "support multiple negation of literals" in {
            PredicateParser("!(!(true))") should be(true)
            PredicateParser("!(!true)") should be(true)
            PredicateParser("!!true") should be(true)
            PredicateParser("!!!(!!(!!true))") should be(false)
            PredicateParser("!(!(false))") should be(false)
            PredicateParser("!(!false)") should be(false)
            PredicateParser("!!false") should be(false)
            PredicateParser("!!!(!!!(!!!false))") should be(true)
        }

        "support basic predicates" in {
            PredicateParser("true && false") should be(false)
            PredicateParser("true && !false") should be(true)
            PredicateParser("!true || !true") should be(false)
            PredicateParser("!!true || false") should be(true)
        }

        "support number comparisons" in {
            PredicateParser("1.7e1 == 17") should be(true)
            PredicateParser("(1.7e1) == (17)") should be(true)
            PredicateParser("!((1.7e1) == (17))") should be(false)
            PredicateParser(".7e1 > -.0") should be(true)
            PredicateParser(".0 == -0") should be(true)
        }

        "support string comparisons" in {
            PredicateParser("AbS == AbS") should be(true)
            PredicateParser("AbS != Abs") should be(true)
            PredicateParser("!(ABS != abs)") should be(false)
        }

        "support operator priority" in {
            PredicateParser("true || false && false") should be(true)
            PredicateParser("(true || false) && false") should be(false)
            PredicateParser("(true && false == false)") should be(true)
        }

        "support different comparisons" in {
            PredicateParser("false || true && 1.7e1 != 17 || ABS == abs") should be(false)
            PredicateParser("!false && !(true && 1.7e1 != 17) && !(ABS == abs)") should be(true)
        }

        "support nested brackets" in {
            PredicateParser("((asd == asd) && (false == false) == true)") should be(true)
        }
    }

    /**
     * TuktuPredicateParser
     */

    "TuktuPredicateParser" should {
        "return correct results for its functions" in {
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
            val parser = new TuktuPredicateParser(datum)

            // isNull
            parser("isNull(null.1)") should be(true)
            parser("isNull(null.2)") should be(true)
            parser("isNull(Int)") should be(false)
            parser("isNull(asd)") should be(false)

            // isNumeric
            parser("isNumeric(JsNumber)") should be(true)
            parser("isNumeric(Double)") should be(true)
            parser("isNumeric(Int)") should be(true)
            parser("isNumeric(String)") should be(false)
            parser("isNumeric(asd)") should be(false)

            // isJSON
            parser("isJSON(null.2)") should be(true)
            parser("isJSON(JsNumber)") should be(true)
            parser("isJSON(JsObject.a.b)") should be(true)
            parser("isJSON(JsObject.a.asd)") should be(false)
            parser("isJSON(JsArray)") should be(true)
            parser("isJSON(empty3)") should be(true)
            parser("isJSON(asd)") should be(false)
            parser("isJSON(Int)") should be(false)
            parser("isJSON(String)") should be(false)
            parser("isJSON(null.2,JsObject.a.b)") should be(true)

            // containsFields
            parser("containsFields(null.1,JsObject.a.b,empty6)") should be(true)
            parser("containsFields(null.1,asd)") should be(false)
            parser("containsFields(" + datum.keys.mkString(",") + ")") should be(true)
            parser("containsFields(" + datum.keys.mkString(",") + ",asd)") should be(false)

            // containsSubstring
            parser("containsSubstring(myString,string)") should be(false)
            parser("containsSubstring(myString,String)") should be(true)
            parser("containsSubstring(String,myString)") should be(false)

            // isEmptyValue
            parser("isEmptyValue(empty1)") should be(true)
            parser("isEmptyValue(empty2)") should be(true)
            parser("isEmptyValue(empty3)") should be(true)
            parser("isEmptyValue(empty4)") should be(true)
            parser("isEmptyValue(empty5)") should be(true)
            parser("isEmptyValue(empty6)") should be(true)
            parser("isEmptyValue(asd)") should be(false)
            parser("isEmptyValue(JsObject)") should be(false)
            parser("isEmptyValue(JsArray)") should be(false)
            parser("isEmptyValue(String)") should be(false)

            // isEmpty
            parser("isEmpty()") should be(false)
            new TuktuPredicateParser(Map())("isEmpty()") should be(true)
        }
    }

}