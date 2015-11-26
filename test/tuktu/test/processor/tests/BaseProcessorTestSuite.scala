package tuktu.test.processor.tests

import org.scalatestplus.play._
import play.api.libs.json.Json
import tuktu.api.DataPacket
import tuktu.processors._
import tuktu.test.processor.BaseProcessorTest
import play.api.libs.json.JsObject

class BaseProcessorTestSuite extends PlaySpec {
    "FieldFilterProcessor" must {
        "filter fields that are specified" in {
            // Processor
            val proc = new FieldFilterProcessor("result")
            
            // Config
            val config = Json.parse("""
              {"fields": [
                  {
                      "default": "",
                      "path": ["key1"],
                      "result": "res1"
                  }
            ]}
            """).as[JsObject]
            
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
            val config = Json.parse("""
                {"fields":["key1", "key2"],
                "ignore_empty_datapackets": true,
                "ignore_empty_datums": true
            }""").as[JsObject]
            
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
    
    "FieldCopyProcessor" must {
        "copy source fields' values to target fields" in {
            // Processor
            val proc = new FieldCopyProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"fields": [
                    {
                        "path": ["key1", "subkey1"],
                        "result": "key3"
                    },
                    {
                        "path": ["key2"],
                        "result": "key4"
                    }
                ]
            }""").as[JsObject]
            
            // Input
            val input = List(new DataPacket(List(
                    Map("key1" -> Map("subkey1" -> "val1"), "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map(
                            "key1" -> Map("subkey1" -> "val1"),
                            "key2" -> "val2",
                            "key3" -> "val1",
                            "key4" -> "val2"
                    )
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "RunningCountProcessor" must {
        // Input
        val input = List(new DataPacket(List(
                Map("one" -> 1),
                Map("one" -> 1),
                Map("one" -> 1)
            )),
            new DataPacket(List(
                Map("one" -> 1),
                Map("one" -> 1),
                Map("one" -> 1)
            )),
            new DataPacket(List(
                Map("one" -> 1),
                Map("one" -> 1),
                Map("one" -> 1)
            ))
        )
        
        // Processor
        val proc = new RunningCountProcessor("result")
        
        
        "compute the running count of DataPackets seen" in {
            // Config
            val config = Json.parse("""
                {"per_block": true}
            """).as[JsObject]
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 0)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 1)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 2),
                    Map("one" -> 1, "result" -> 2),
                    Map("one" -> 1, "result" -> 2)
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
        
        "compute the running count of Datums seen" in {
            // Config
            val config = Json.parse("{}").as[JsObject]
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 2)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 3),
                    Map("one" -> 1, "result" -> 4),
                    Map("one" -> 1, "result" -> 5)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 6),
                    Map("one" -> 1, "result" -> 7),
                    Map("one" -> 1, "result" -> 8)
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
        
        "compute the running count of Datums seen, with a step size" in {
            // Config
            val config = Json.parse("""
                {
                    "step_size": 3
                }
            """).as[JsObject]
            
            //Expected output
            val output = List(new DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 3),
                    Map("one" -> 1, "result" -> 6)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 9),
                    Map("one" -> 1, "result" -> 12),
                    Map("one" -> 1, "result" -> 15)
                )),
                new DataPacket(List(
                    Map("one" -> 1, "result" -> 18),
                    Map("one" -> 1, "result" -> 21),
                    Map("one" -> 1, "result" -> 24)
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
}