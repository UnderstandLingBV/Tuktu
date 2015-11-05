package tuktu.test.flow.tests

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers
import org.scalatest.FunSuite
import tuktu.test.flow.BaseFlowTester
import tuktu.api.DataPacket

class DummyTest extends FunSuite with Matchers with ScalaFutures {
    test("DummyTest flow that generates a single value only") {
        val data = List(new DataPacket(List(Map("test" -> "test"))))
        val testResult = new BaseFlowTester()(List(data), "flowtests/dummy")

        testResult.futureValue should equal(true)
    }
}