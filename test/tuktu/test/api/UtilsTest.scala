package tuktu.test.api

import org.scalatest._
import org.scalatestplus.play.PlaySpec
import tuktu.api.utils

@DoNotDiscover
class utilsTests extends PlaySpec {
    /**
     * evaluateTuktuString
     */
    "evaluateTuktuString" must {
        "support nested Tuktu Strings" in {
            val obtained = utils.evaluateTuktuString("", Map.empty)
            val expected = ""
            assertResult(true, "Obtained result is " + obtained + " but expected " + expected) {
                obtained == expected
            }
        }
    }
}