package tuktu.test.flow.tests

import org.scalatest.DoNotDiscover
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec

import play.api.libs.concurrent.Akka
import tuktu.api.DataPacket
import tuktu.test.flow.BaseFlowTester
import org.scalatest.BeforeAndAfter

import play.api.Play.current

@DoNotDiscover
class FlowTests extends PlaySpec {
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
                Map("num" -> -0.6))))
            new BaseFlowTester(Akka.system)(List(data), "flowtests/normalization")
        }
    }
}