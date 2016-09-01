package tuktu.test.processor.tests

import org.scalatestplus.play._
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.processors._
import tuktu.test.processor.BaseProcessorTest
import play.api.libs.json.JsObject

class BufferProcessorTestSuite extends PlaySpec {
    "SizeBufferProcessor" must {
        "buffer DataPackets until a certain amount is reached" in {
            // Processor
            val proc = new SizeBufferProcessor("")

            // Config
            val config = Json.obj("size" -> 2)

            // Input
            val input = List(
                DataPacket(List(Map("key" -> 1), Map("key" -> 2))),
                DataPacket(List(Map("key" -> 3))),
                DataPacket(List(Map("key" -> 4))))

            //Expected output
            val output = List(
                DataPacket(List(Map("key" -> 1), Map("key" -> 2), Map("key" -> 3))),
                DataPacket(List(Map("key" -> 4))))

            new BaseProcessorTest()(proc, config, input, output)
        }
    }

    "EOFBufferProcessor" must {
        "buffer all DataPackets until EOF is reached" in {
            // Processor
            val proc = new EOFBufferProcessor("")

            // Config
            val config = Json.obj()

            // Input
            val input = List(
                DataPacket(List(Map("key" -> 1), Map("key" -> 2))),
                DataPacket(List(Map("key" -> 3))),
                DataPacket(List(Map("key" -> 4))))

            //Expected output
            val output = List(
                DataPacket(List(Map("key" -> 1), Map("key" -> 2), Map("key" -> 3), Map("key" -> 4))))

            new BaseProcessorTest()(proc, config, input, output)
        }
    }

    "DataPacketSplitterProcessor" must {
        "split all Datums of a DataPacket into separate DataPackets" in {
            // Processor
            val proc = new DataPacketSplitterProcessor("")

            // Config
            val config = Json.obj()

            // Input
            val input = List(
                DataPacket(List(Map("key" -> 1), Map("key" -> 2))),
                DataPacket(List(Map("key" -> 3))),
                DataPacket(List(Map("key" -> 4))))

            //Expected output
            val output = List(
                DataPacket(List(Map("key" -> 1))),
                DataPacket(List(Map("key" -> 2))),
                DataPacket(List(Map("key" -> 3))),
                DataPacket(List(Map("key" -> 4))))

            new BaseProcessorTest()(proc, config, input, output)
        }
    }
}