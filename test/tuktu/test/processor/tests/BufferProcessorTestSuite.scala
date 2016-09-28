package tuktu.test.processor.tests

import org.scalatest.DoNotDiscover
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import org.scalatest.BeforeAndAfter

import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api.DataPacket
import tuktu.processors.DataPacketSplitterProcessor
import tuktu.processors.EOFBufferProcessor
import tuktu.processors.GroupByProcessor
import tuktu.processors.SizeBufferProcessor
import tuktu.test.processor.BaseProcessorTest
import play.api.libs.concurrent.Akka
import play.api.Play

@DoNotDiscover
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
    
    /*"GroupByProcessor" must {
        "group Datums into separate DataPackets based on their values in given fields" in {

            // Processor
            val proc = new GroupByProcessor(null, "group")

            // Config
            val config = Json.obj("fields" -> List("key1", "key2"))

            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 3),
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 4)
                )),
                DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 5),
                    Map("key1" -> 1, "key2" -> 3, "key3" -> 6)
                ))
            )

            // Expected output, order irrelevant within each DP's groups, so n! possible outputs for n different groups
            // In this case we have 1! = 1 output for the first, and 2! = 2 ouputs for the second DataPacket, hence 2 total outputs
            val output1 = List(DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 3),
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 4)
                )),
                DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 5)
                )),
                DataPacket(List(
                    Map("key1" -> 1, "key2" -> 3, "key3" -> 6)
                ))
            )

            val output2 = List(DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 3),
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 4)
                )),
                DataPacket(List(
                    Map("key1" -> 1, "key2" -> 3, "key3" -> 6)
                )),
                DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 5)
                ))
            )

            new BaseProcessorTest()(proc, config, input, output1, output2)
        }
    }*/
}