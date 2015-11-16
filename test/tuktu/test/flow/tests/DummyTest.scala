package tuktu.test.flow.tests

import org.scalatest.concurrent.ScalaFutures
import tuktu.test.flow.BaseFlowTester
import tuktu.api.DataPacket
import org.scalatestplus.play._
import play.api.test.FakeApplication
import play.api.test._
import play.api.test.Helpers._

class DummyTest extends PlaySpec with ScalaFutures {
    "DummyTest flow" must {
        "generate one simple value" in {
            running(FakeApplication()) {
                val data = List(new DataPacket(List(Map("test" -> "test"))))
                val testResult = new BaseFlowTester()(List(data), "flowtests/dummy")
        
                testResult.futureValue must equal(true)
            }
        }
    }
}