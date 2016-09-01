package tuktu.test.flow.tests

import org.scalatestplus.play._

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import tuktu.api.DataPacket
import tuktu.test.flow.BaseFlowTester

class FlowTests extends PlaySpec with OneAppPerSuite {
    "DummyTest flow" must {
        "generate one simple value" in {
            val data = List(DataPacket(List(Map("test" -> "test"))))
            new BaseFlowTester(Akka.system)(List(data), "flowtests/dummy")
        }
    }
    
    "Normalization flow" must {
        "normalize values to range [-1, 1]" in {
            val data = List(DataPacket(List(
                    Map("num" -> 0.6),
                    Map("num" -> 1.0),
                    Map("num" -> -1.0),
                    Map("num" -> -0.6)
            )))
            new BaseFlowTester(Akka.system)(List(data), "flowtests/normalization")
        }
    }
}