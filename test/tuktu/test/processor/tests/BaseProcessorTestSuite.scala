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
                    Map("res1" -> "val1"),
                    Map()
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "FieldRemoveProcessor" must {
        "remove fields that are specified" in {
            // Processor
            val proc = new FieldRemoveProcessor("result")
            
            // Config
            val config = Json.obj("fields" -> Json.arr(
                    "key1", "key2"
                ),
                "ignore_empty_datapackets" -> true,
                "ignore_empty_datums" -> true
            )
            
            // Input
            val input = List(new DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2"),
                    Map("key3" -> "val3")
                )),
                new DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map("key3" -> "val3")
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    /*"FieldCopyProcessor" must {
        "copy source fields' values to target fields" in {
            // Processor
            val proc = new FieldCopyProcessor("result")
            
            // Config
            val config = Json.obj("fields" -> Json.arr(
                    Json.obj(
                            "path" -> Json.arr(
                                    "key1", "subkey1"
                            ),
                            "result" -> "key3"
                    ),
                    Json.obj(
                            "path" -> Json.arr(
                                    "key2"
                            ),
                            "result" -> "key4"
                    )
                )
            )
            
            // Input
            val input = List(new DataPacket(List(
                    Map("key1" -> Map("subkey1" -> "val1"), "key2" -> "val2"),
                    Map("key3" -> "val3")
                ))
            )
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map(
                            "key1" -> Map("subkey1" -> "val1"),
                            "key2" -> "val2",
                            "key3" -> "val1",
                            "key4" -> "val2"
                    ),
                    Map("key3" -> "val3")
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }*/
    }
}