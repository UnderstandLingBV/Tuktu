package tuktu.test.processor.tests

import org.scalatestplus.play._
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.processors._
import tuktu.test.processor.BaseProcessorTest

class BaseProcessorTestSuite extends PlaySpec {
    "FieldFilterProcessor" must {
        "filter fields that are specified" in {
            // Processor
            val proc = new FieldFilterProcessor("result")
            
            // Config
            val config = Json.obj("fields" -> Json.arr(
                    Json.obj(
                            "default" -> "",
                            "path"-> Json.arr(
                                    "key1"
                            ),
                            "result" -> "res1"
                    )
            ))
            
            // Input
            val input = List(new DataPacket(List(
                Map("key1" -> "val1", "key2" -> "val2"),
                Map("key2" -> "val2")
            )))
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map("key1" -> "val1"),
                    Map()
            )))
            
            new BaseProcessorTest()(proc, config, input, output) mustBe true
        }
    }
}