package tuktu.test.flow.tests

import org.scalatestplus.play._

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import tuktu.api.DataPacket
import tuktu.test.flow.BaseFlowTester

class DummyTest extends PlaySpec with OneAppPerSuite {
    "DummyTest flow" must {
        "generate one simple value" in {
            val data = List(new DataPacket(List(Map("test" -> "test"))))
            new BaseFlowTester(Akka.system)(List(data), "flowtests/dummy")
        }
    }
}