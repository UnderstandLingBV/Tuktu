package tuktu.test.api

import play.api.libs.json._
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
    }

    "evaluateTuktuJsString" should {
        "parse a JSON string to the correct type" in {
            val datum: Map[String, Any] = Map("List" -> List(1, 2, 3))
            utils.evaluateTuktuJsString(JsString("$JSON.parse{$JSON.stringify{List}}"), datum, '$') should be(Json.arr(1, 2, 3))
        }
    }

}