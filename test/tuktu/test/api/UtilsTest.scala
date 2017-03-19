package tuktu.test.api

import play.api.libs.json._
import tuktu.test.testUtil
import tuktu.api.utils
import org.scalatest._
import org.scalatestplus.play.PlaySpec
import Matchers._

@DoNotDiscover
class utilsTests extends PlaySpec {

    "evaluateTuktuString" should {
        "support nested Tuktu Strings" in {
            val datum: Map[String, Any] = Map("key" -> "a", "sub" -> "b.c", "a" -> Json.obj("b" -> Json.obj("c" -> "1")), "1" -> 17)
            utils.evaluateTuktuString("${${${key}.${sub}}}", datum, '$') should be("17")
        }

        "JSON.stringify different types" in {
            val datum: Map[String, Any] = Map("List" -> List(1, "2", Map("a" -> 3)))
            utils.evaluateTuktuString("$JSON.stringify{List}", datum, '$') should be(Json.arr(1, "2", Json.obj("a" -> 3)).toString)
        }

        "process SQL parameters" in {
            val datum: Map[String, Any] = Map("Int" -> 17, "String" -> "Us'er", "Boolean" -> true)
            utils.evaluateTuktuString("INSERT INTO `db`.`table` VALUES($SQL{Int}, $SQL{String}, $SQL{Boolean});", datum, '$') should be("INSERT INTO `db`.`table` VALUES(17, 'Us''er', 1);")
        }
        
        "process SplitGet" in {
            val datum: Map[String, Any] = Map("key" -> "some split string")
            utils.evaluateTuktuString("$SplitGet{key, ,1}", datum, '$') should be("split")
        }
    }

    "evaluateTuktuJsString" should {
        "parse a JSON string to the correct type" in {
            val datum: Map[String, Any] = Map("List" -> List(1, 2, 3))
            utils.evaluateTuktuJsString(JsString("$JSON.parse{$JSON.stringify{List}}"), datum, '$') should be(Json.arr(1, 2, 3))
        }
    }

    "mergeJson" should {
        "merge key-disjoint objects" in {
            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("b" -> 3)),
                    Json.obj("c" -> Json.arr(true, 1, "17"))),
                Json.obj("c" -> Json.arr(true, 1, "17"), "a" -> Json.obj("b" -> 3)),
                false) should be(true)

            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("b" -> 3)),
                    Json.obj("c" -> Json.arr(true, 1, "17"))),
                Json.obj("c" -> Json.arr(1, true, "17"), "a" -> Json.obj("b" -> 3)),
                false) should be(false)

            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("b" -> 3)),
                    Json.obj("c" -> Json.arr(true, 1, "17"))),
                Json.obj("c" -> Json.arr("17", 1, true), "a" -> Json.obj("b" -> 3)),
                true) should be(true)
        }

        "merge objects by overwriting everything with the values of the second argument" in {
            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("c" -> Json.arr(1), "d" -> false)),
                    Json.obj("a" -> Json.obj("c" -> Json.arr(true, 1, "17")))),
                Json.obj("a" -> Json.obj("c" -> Json.arr(true, 1, "17"), "d" -> false)),
                false) should be(true)

            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("c" -> Json.arr(1), "d" -> false)),
                    Json.obj("a" -> Json.obj("c" -> Json.arr(true, 1, "17")))),
                Json.obj("a" -> Json.obj("c" -> Json.arr(1, true, "17"), "d" -> false)),
                false) should be(false)

            testUtil.inspectJsValue(
                utils.mergeJson(
                    Json.obj("a" -> Json.obj("c" -> Json.arr(1), "d" -> false)),
                    Json.obj("a" -> Json.obj("c" -> Json.arr(true, 1, "17")))),
                Json.obj("a" -> Json.obj("c" -> Json.arr(1, true, "17"), "d" -> false)),
                true) should be(true)
        }
    }

    "mergeConfig" should {
        "merge generators by index" in {
            testUtil.inspectJsValue(
                utils.mergeConfig(
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj("a" -> 1),
                            Json.obj("a" -> 2)),
                        "processors" -> Json.arr()),
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj("a" -> 3)),
                        "processors" -> Json.arr())),
                Json.obj(
                    "generators" -> Json.arr(
                        Json.obj("a" -> 3),
                        Json.obj("a" -> 2)),
                    "processors" -> Json.arr()),
                false) should be(true)

            testUtil.inspectJsValue(
                utils.mergeConfig(
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj("a" -> 1),
                            Json.obj("a" -> 2)),
                        "processors" -> Json.arr()),
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj(),
                            Json.obj("a" -> 3)),
                        "processors" -> Json.arr())),
                Json.obj(
                    "generators" -> Json.arr(
                        Json.obj("a" -> 1),
                        Json.obj("a" -> 3)),
                    "processors" -> Json.arr()),
                false) should be(true)

            testUtil.inspectJsValue(
                utils.mergeConfig(
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj("a" -> 1),
                            Json.obj("a" -> 2)),
                        "processors" -> Json.arr()),
                    Json.obj(
                        "generators" -> Json.arr(
                            Json.obj(),
                            Json.obj(),
                            Json.obj("a" -> 3)),
                        "processors" -> Json.arr())),
                Json.obj(
                    "generators" -> Json.arr(
                        Json.obj("a" -> 1),
                        Json.obj("a" -> 2),
                        Json.obj("a" -> 3)),
                    "processors" -> Json.arr()),
                false) should be(true)
        }

        "merge processors by id" in {
            testUtil.inspectJsValue(
                utils.mergeConfig(
                    Json.obj(
                        "generators" -> Json.arr(),
                        "processors" -> Json.arr(
                            Json.obj(
                                "id" -> "b",
                                "b" -> "b"),
                            Json.obj(
                                "id" -> "a",
                                "b" -> "a"))),
                    Json.obj(
                        "generators" -> Json.arr(),
                        "processors" -> Json.arr(
                            Json.obj(
                                "id" -> "a",
                                "b" -> "c")))),
                Json.obj(
                    "generators" -> Json.arr(),
                    "processors" -> Json.arr(
                        Json.obj(
                            "id" -> "b",
                            "b" -> "b"),
                        Json.obj(
                            "id" -> "a",
                            "b" -> "c"))),
                true) should be(true)

            testUtil.inspectJsValue(
                utils.mergeConfig(
                    Json.obj(
                        "generators" -> Json.arr(),
                        "processors" -> Json.arr(
                            Json.obj(
                                "id" -> "b",
                                "b" -> "b"),
                            Json.obj(
                                "id" -> "a",
                                "b" -> "a"))),
                    Json.obj(
                        "generators" -> Json.arr(),
                        "processors" -> Json.arr(
                            Json.obj(
                                "id" -> "c",
                                "b" -> "c")))),
                Json.obj(
                    "generators" -> Json.arr(),
                    "processors" -> Json.arr(
                        Json.obj(
                            "id" -> "b",
                            "b" -> "b"),
                        Json.obj(
                            "id" -> "a",
                            "b" -> "a"),
                        Json.obj(
                            "id" -> "c",
                            "b" -> "c"))),
                true) should be(true)
        }
    }

    "nearlyEqual" should {
        "compare positive large numbers" in {
            utils.nearlyEqual(1000000000d, 1000000001d) should be(true)
            utils.nearlyEqual(100000000d, 100000001d) should be(false)
        }

        "compare negative large numbers" in {
            utils.nearlyEqual(-1000000000d, -1000000001d) should be(true)
            utils.nearlyEqual(-100000000d, -100000001d) should be(false)
        }

        "compare numbers around 1" in {
            utils.nearlyEqual(1.000000000, 1.000000001) should be(true)
            utils.nearlyEqual(1.00000000, 1.00000001) should be(false)
        }

        "compare numbers around -1" in {
            utils.nearlyEqual(-1.000000000, -1.000000001) should be(true)
            utils.nearlyEqual(-1.00000000, -1.00000001) should be(false)
        }

        "compare numbers between 0 and 1" in {
            utils.nearlyEqual(0.0000000001000000001, 0.0000000001000000002) should be(true)
            utils.nearlyEqual(0.000000000100000001, 0.000000000100000002) should be(false)
        }

        "compare numbers between 0 and -1" in {
            utils.nearlyEqual(-0.0000000001000000001, -0.0000000001000000002) should be(true)
            utils.nearlyEqual(-0.000000000100000001, -0.000000000100000002) should be(false)
        }

        "compare numbers involving 0" in {
            utils.nearlyEqual(0.0, 0.0) should be(true)
            utils.nearlyEqual(0.0, -0.0) should be(true)
            utils.nearlyEqual(-0.0, -0.0) should be(true)
            utils.nearlyEqual(0.0000000001, -0.0) should be(false)
            utils.nearlyEqual(-0.0000000001, 0.0) should be(false)
        }

        "compare extreme values" in {
            utils.nearlyEqual(Double.MaxValue, Double.MaxValue) should be(true)
            utils.nearlyEqual(Double.MaxValue, -Double.MaxValue) should be(false)
            utils.nearlyEqual(Double.MaxValue, Double.MinValue) should be(false)
            utils.nearlyEqual(Double.MaxValue, -Double.MinValue) should be(true)
            utils.nearlyEqual(Double.MaxValue, Double.MaxValue / 2) should be(false)
        }

        "compare infinities" in {
            utils.nearlyEqual(Double.PositiveInfinity, Double.PositiveInfinity) should be(true)
            utils.nearlyEqual(Double.NegativeInfinity, Double.NegativeInfinity) should be(true)
            utils.nearlyEqual(Double.PositiveInfinity, -Double.NegativeInfinity) should be(true)
            utils.nearlyEqual(Double.PositiveInfinity, Double.NegativeInfinity) should be(false)
            utils.nearlyEqual(Double.PositiveInfinity, Double.MaxValue) should be(false)
            utils.nearlyEqual(Double.NegativeInfinity, Double.MinValue) should be(false)
        }

        "compare NaN" in {
            utils.nearlyEqual(Double.NaN, Double.NaN) should be(false)
            utils.nearlyEqual(Double.NaN, Double.NegativeInfinity) should be(false)
            utils.nearlyEqual(Double.NaN, Double.MinValue) should be(false)
            utils.nearlyEqual(Double.NaN, Double.MinPositiveValue) should be(false)
            utils.nearlyEqual(Double.NaN, Double.MaxValue) should be(false)
            utils.nearlyEqual(Double.NaN, Double.PositiveInfinity) should be(false)
            utils.nearlyEqual(Double.NaN, 0.0) should be(false)
            utils.nearlyEqual(Double.NaN, 1e40) should be(false)
            utils.nearlyEqual(Double.NaN, -1e40) should be(false)
            utils.nearlyEqual(Double.NaN, 1e-40) should be(false)
            utils.nearlyEqual(Double.NaN, -1e-40) should be(false)
        }

        "compare numbers very close to 0" in {
            utils.nearlyEqual(Double.MinPositiveValue, Double.MinPositiveValue) should be(true)
            utils.nearlyEqual(Double.MinPositiveValue, -Double.MinPositiveValue) should be(true)
            utils.nearlyEqual(Double.MinPositiveValue, 0) should be(true)
            utils.nearlyEqual(-Double.MinPositiveValue, 0) should be(true)
            utils.nearlyEqual(0.0000000000001, Double.MinPositiveValue) should be(false)
        }
    }

}