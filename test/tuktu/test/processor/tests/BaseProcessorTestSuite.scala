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
            val input = List(DataPacket(List(
                Map("key1" -> "val1", "key2" -> "val2"),
                Map("key2" -> "val2")
            )))
            
            //Expected output
            val output = List(DataPacket(List(
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
            val config = Json.parse("""{"fields":["key1", "key2"]}""").as[JsObject]

            // Input
            val input = List(
                DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2"),
                    Map("key3" -> "val3")
                )),
                DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2")
                ))
            )

            //Expected output
            val output = List(
                DataPacket(List(
                    Map(),
                    Map("key3" -> "val3")
                )),
                DataPacket(List(
                    Map()
                ))
            )

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
            val input = List(DataPacket(List(
                    Map("key1" -> Map("subkey1" -> "val1"), "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
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
        val input = List(DataPacket(List(
                Map("one" -> 1),
                Map("one" -> 1),
                Map("one" -> 1)
            )),
            DataPacket(List(
                Map("one" -> 1),
                Map("one" -> 1),
                Map("one" -> 1)
            )),
            DataPacket(List(
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
            val output = List(DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 0)
                )),
                DataPacket(List(
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 1)
                )),
                DataPacket(List(
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
            val output = List(DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 1),
                    Map("one" -> 1, "result" -> 2)
                )),
                DataPacket(List(
                    Map("one" -> 1, "result" -> 3),
                    Map("one" -> 1, "result" -> 4),
                    Map("one" -> 1, "result" -> 5)
                )),
                DataPacket(List(
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
            val output = List(DataPacket(List(
                    Map("one" -> 1, "result" -> 0),
                    Map("one" -> 1, "result" -> 3),
                    Map("one" -> 1, "result" -> 6)
                )),
                DataPacket(List(
                    Map("one" -> 1, "result" -> 9),
                    Map("one" -> 1, "result" -> 12),
                    Map("one" -> 1, "result" -> 15)
                )),
                DataPacket(List(
                    Map("one" -> 1, "result" -> 18),
                    Map("one" -> 1, "result" -> 21),
                    Map("one" -> 1, "result" -> 24)
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "ReplaceProcessor" must {
        "replace one string (regex) with another" in {
            // Processor
            val proc = new ReplaceProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"field":"key1",
                "sources": ["[0-9]+"],
                "targets": ["2"]
            }""").as[JsObject]
              
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("key1" -> "val2", "key2" -> "val2")
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "JsonFetcherProcessor" must {
      "get a JSON Object and fetches a single field to put it as top-level citizen of the data" in {
        // Processor
        val proc = new JsonFetcherProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"fields": [
            {
              "path": ["key2","key3"],
              "result": "key4"
            }
          ]
        }""").as[JsObject]
        
        // Input
        val input = List(DataPacket(List(
              Map("json" -> Json.obj("key1" -> "val1", "key2" -> Json.obj("key3" -> "val3")))
            ))
        )
        
        // Expected output
        val output = List(DataPacket(List(
              Map("json" -> Json.obj("key1" -> "val1", "key2" -> Json.obj("key3" -> "val3")), "key4" -> "val3")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
    }
    
    
     "FieldRenameProcessor" must {
        "rename source fields' values to target fields" in {
            // Processor
            val proc = new FieldRenameProcessor("result")
            
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
            val input = List(DataPacket(List(
                    Map("key1" -> Map("subkey1" -> "val1"), "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
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
     
    "PacketFilterProcessor" must {
      
         // Input
        val input = List(
            DataPacket(List(
              Map("key1" -> 1, "key2" -> 3, "key3" -> "value1"),
              Map("key1" -> 2, "key2" -> 2, "key3" -> "value2"),
              Map("key1" -> 3, "key2" -> 1, "key3" -> "value3")
            ))
        )
      
      "filter out datapackets that satisfy multiple joint-conditions" in {
        // Processor
        val proc = new PacketFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "expression": [{"expression":"${key2} > 1", "type":"simple"},{"expression":"${key2} < 3", "type":"simple"}]
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> 2, "key2" -> 2, "key3" -> "value2")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
        
      "filter out datapackets that satisfy multiple disjoint-conditions" in {
        // Processor
        val proc = new PacketFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "and_or": "or",
              "expression": [{"expression":"${key2} > 2", "type":"simple"},{"expression":"${key2} < 2", "type":"simple"}]
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> 1, "key2" -> 3, "key3" -> "value1"),
              Map("key1" -> 3, "key2" -> 1, "key3" -> "value3")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
      
      "filter out datapackets that satisfy a negate-condition" in {
        // Processor
        val proc = new PacketFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "negate",
              "expression": "${key3} == value3"
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> 1, "key2" -> 3, "key3" -> "value1"),
              Map("key1" -> 2, "key2" -> 2, "key3" -> "value2")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
      
      "filter out datapackets that satisfy a groovy-condition" in {
        // Processor
        val proc = new PacketFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "groovy",
              "expression": "'value' + '${key1}' == '${key3}'"
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> 1, "key2" -> 3, "key3" -> "value1"),
              Map("key1" -> 2, "key2" -> 2, "key3" -> "value2"),
              Map("key1" -> 3, "key2" -> 1, "key3" -> "value3")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
     
      "filter out datapackets that satisfy a batch-condition" in {
        // Processor
        val proc = new PacketFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "expression": "${key1} > 1"
            }
          ], "batch" : true,
          "batch_min_count" : 2
          
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> 1, "key2" -> 3, "key3" -> "value1"),
              Map("key1" -> 2, "key2" -> 2, "key3" -> "value2"),
              Map("key1" -> 3, "key2" -> 1, "key3" -> "value3")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
    }
    
    "PacketRegexFilterProcessor" must {
      
      // Input
      val input = List(
        DataPacket(List(
          Map("key1" -> "een", "key2" -> "twee"),
          Map("key1" -> "half", "key2" -> "een"),
          Map("key1" -> "1", "key2" -> "2")
        ))
      )
      
      "filter out datapackets that satisfy a number of regular disjoint-expressions" in {
        // Processor
        val proc = new PacketRegexFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "and_or": "or",
              "expression": "[0-9]+",
              "field": "key1"
            },
            {
              "type": "simple",
              "and_or": "or",
              "expression": "een",
              "field": "key1"
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> "een", "key2" -> "twee"),
              Map("key1" -> "1", "key2" -> "2")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
      
      "filter out datapackets that satisfy a number of regular joint-expressions" in {
        // Processor
        val proc = new PacketRegexFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "and_or": "and",
              "expression": "e",
              "field": "key2"
            },
            {
              "type": "simple",
              "and_or": "and",
              "expression": "w",
              "field": "key2"
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> "een", "key2" -> "twee")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
      "filter out datapackets that satisfy a regular negate-expression" in {
        // Processor
        val proc = new PacketRegexFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "negate",
              "and_or": "or",
              "expression": "een",
              "field": "key1"
            }
          ]
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
            Map("key1" -> "half", "key2" -> "een"),
              Map("key1" -> "1", "key2" -> "2")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
      "filter out datapackets that satisfy a regular batch-expression" in {
        // Processor
        val proc = new PacketRegexFilterProcessor("result")
        
        // Config
        val config = Json.parse("""
          {"expressions": [
            {
              "type": "simple",
              "and_or": "or",
              "expression": "[0-9]+",
              "field": "key1"
            },
            {
              "type": "simple",
              "and_or": "or",
              "expression": "half",
              "field": "key1"
            }
          ], "batch" : true,
          "batch_min_count" : 2
        }""").as[JsObject]
        
        // Expected output
        val output = List(DataPacket(List(
              Map("key1" -> "een", "key2" -> "twee"),
              Map("key1" -> "half", "key2" -> "een"),
              Map("key1" -> "1", "key2" -> "2")
            ))
        )
        
        new BaseProcessorTest()(proc, config, input, output)
      }
    }
    
    "FieldConstantAdderProcessor" must {
        "overwrite field 'result'" in {
            // Processor
            val proc = new FieldConstantAdderProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"value": "val1"
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> "val2", "key2" -> "val2", "result" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                Map("key1" -> "val2", "key2" -> "val2", "result" -> "val1")    
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
        "add a field with a constant value" in {
            // Processor
            val proc = new FieldConstantAdderProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"value": "val1"
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> "val2", "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                Map("key1" -> "val2", "key2" -> "val2", "result" -> "val1")    
            )))
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "ConsoleWriterProcessor" must {
        "dump data to console" in {
            // Processor
            val proc = new ConsoleWriterProcessor("result")
            
            // Config
            val config = Json.parse("{}").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                     Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
        "prettify da dump" in {
            // Processor
            val proc = new ConsoleWriterProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "prettify" : true
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                     Map("key1" -> "val1", "key2" -> "val2")
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "StringImploderProcessor" must {
        "implode an array of strings" in {
            // Processor
            val proc = new StringImploderProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"fields": [
                    {
                        "path": ["key1"],
                        "separator": ","
                    }
                ]
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> Json.arr("val1","val2","val3"), "key2" -> Json.arr("val4","val5","val6"))
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("key1" -> "val1,val2,val3", "key2" -> Json.arr("val4","val5","val6"))
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "KeyImploderProcessor" must {
        "implode all elements in a Datapacket into a single element of a datapacket" in {
            // Processor
            val proc = new KeyImploderProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"fields": ["keyholes"]
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("keyholes" -> "keyhole1"),Map("keyholes" -> "keyhole2"),Map("keyholes" -> "keyhole3")
                ))
            )
            
            //Expected output
            val output = List(DataPacket(List(
                     Map("keyholes" -> List("keyhole1","keyhole2","keyhole3"))
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "JsObjectImploderProcessor" must {
        "implode an array of JSON object-fields into an array of JSON strings found at a specified sub-path, which is then joined by a given separator, overwriting its top-level ancestor" in {
            // Processor
            val proc = new JsObjectImploderProcessor("result")
            
            // Config
            val config = Json.parse("""
                {"fields": [
                    {
                        "path": ["keys"],
                        "subpath": ["key1"],
                        "separator": ","
                    }
                ]
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("keys" ->  Json.arr(Json.obj("key1" -> "val1"),Json.obj("key1" -> "val2"),Json.obj("key1" -> "val3"))) 
                ))
            )
            
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("keys" -> "val1,val2,val3")
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "FlattenerProcessor" must {
        "recursively flatten a map object, appending the keys to the previous keys separated by a given separator" in {
            // Processor
            val proc = new FlattenerProcessor("result")
            
            // Config
            val config = Json.parse("""
                {
                  "fields": ["key1"],
                  "separator": ","
            }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> Map("subkey1" -> "val1"))
                ))
            )
            
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("key1,subkey1" -> "val1")
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "SequenceExploderProcessor" must {
        "return packets for each value in a sequence object" in {
          
            // Processor
            val proc = new SequenceExploderProcessor("result")
            
            // Config
            val config = Json.parse("""
                  {"field": "keyholes"}
                """).as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("keyholes" -> List("keyhole1","keyhole2","keyhole3"))
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("keyholes" -> "keyhole1"),Map("keyholes" -> "keyhole2"),Map("keyholes" -> "keyhole3")
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "StringSplitterProcessor" must {
      
      // Input
      val input = List(DataPacket(List(
          Map("key1" -> "value1,value2,value3", "key2" -> "value4")
        ))
      ) 
      "split a string up into a list of values based on a separator and overwrite the old field" in {
          
            // Processor
            val proc = new StringSplitterProcessor("result")
            
            // Config
            val config = Json.parse("""{
                 "field": "key1",
                 "separator": ",",
                 "overwrite": true
              }""").as[JsObject]

            //Expected output
            val output = List(DataPacket(List(
                    Map("key1" -> List("value1","value2","value3"), "key2" -> "value4")
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
        "split a string up into a list of values based on a separator and do not overwrite the old field" in {
          
            // Processor
            val proc = new StringSplitterProcessor("result")
            
            // Config
            val config = Json.parse("""{
                 "field": "key1",
                 "separator": ",",
                 "overwrite": false
              }""").as[JsObject]
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("key1" -> "value1,value2,value3", "key2" -> "value4", "result" -> List("value1","value2","value3"))
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "ListMapFlattenerProcessor" must {
        "read out a specific key of each map in a list" in {
          
            // Processor
            val proc = new ListMapFlattenerProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "list_field": "keys",
                "map_field": "key1"
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List( Map("keys" -> List(
                    Map("key1" -> "value1", "key2" -> "value2"),
                    Map("key1" -> "value3", "key2" -> "value4"),
                    Map("key1" -> "value5", "key2" -> "value6")))
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(
                    Map("keys" -> List("value1","value3","value5"))
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }

    "MultiListMapFlattenerProcessor" must {
        "read out specific keys of each map in a list" in {
          
            // Processor
            val proc = new MultiListMapFlattenerProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "list_field": "keys",
                "map_fields": ["key1","key3"]
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(Map("keys" -> List(
                    Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3"),
                    Map("key1" -> "value4", "key2" -> "value5", "key3" -> "value6"),
                    Map("key1" -> "value7", "key2" -> "value8", "key3" -> "value9")))
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(Map("keys" -> List(
                    Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3"),
                    Map("key1" -> "value4", "key2" -> "value5", "key3" -> "value6"),
                    Map("key1" -> "value7", "key2" -> "value8", "key3" -> "value9")),
                    "key1" -> List("value1","value4","value7"),
                    "key3" -> List("value3","value6","value9"))
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "ContainsAllFilterProcessor" must {
        "Verifiy that all fields are present before sending it on" in {
          
            // Processor
            val proc = new ContainsAllFilterProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "field": "key1",
                "contains_field": "values",
                "field_list": "maps"
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(Map("maps" -> List(
                   Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3"),
                   Map("key1" -> "value4", "key2" -> "value5", "key3" -> "value6"),
                   Map("key1" -> "value7", "key2" -> "value8", "key3" -> "value9")),
                   "values" -> List("value1"),
                   "field" -> "key1")
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(Map("maps" -> List(
                   Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3"),
                   Map("key1" -> "value4", "key2" -> "value5", "key3" -> "value6"),
                   Map("key1" -> "value7", "key2" -> "value8", "key3" -> "value9")),
                   "values" -> List("value1"),
                   "field" -> "key1")
                )) 
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "MapFlattenerProcessor" must {
        "make a Map as top-level citizen" in {
          
            // Processor
            val proc = new MapFlattenerProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "field": "keys"
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("keys" -> Map("key1" -> "value1", "key2" -> "value2"))
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(
                   Map("keys" -> Map("key1" -> "value1", "key2" -> "value2"), "key1" -> "value1", "key2" -> "value2")
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }
    
    "ZipExplodeProcessor" must {
        "zip and explode two two traversables" in {
          
            // Processor
            val proc = new ZipExplodeProcessor("result")
            
            // Config
            val config = Json.parse("""{
                "field_1": "keys",
                "field_2": "values"
              }""").as[JsObject]
            
            // Input
            val input = List(DataPacket(List(
                    Map("keys" -> List("key1", "key2"),"values" -> List("value1", "value2"))
                ))
            ) 
            
            //Expected output
            val output = List(DataPacket(List(
                   Map("keys" -> "key1", "values" -> "value1"), Map("keys" -> "key2", "values" -> "value2")
                ))
            )
            
            new BaseProcessorTest()(proc, config, input, output)
        }
    }

    "GroupByProcessor" must {
        "group Datums into separate DataPackets based on their values in given fields" in {

            // Processor
            val proc = new GroupByProcessor("")

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
    }

    "AbsentFieldsFilterProcessor" must {
        "filter Datums which don't contain all of the given fields" in {

            // Processor
            val proc = new AbsentFieldsFilterProcessor("")

            // Config
            val config = Json.obj("fields" -> List("key1", "key2"))

            // Input
            val input = List(DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 3),
                    Map("key1" -> 4, "key3" -> 5, "key4" -> 6)
                )),
                DataPacket(List(
                    Map("key1" -> 7, "key3" -> 8, "key4" -> 9),
                    Map("key1" -> 10, "key3" -> 11, "key4" -> 12)
                ))
            )

            //Expected output
            val output = List(DataPacket(List(
                    Map("key1" -> 1, "key2" -> 2, "key3" -> 3)
                ))
            )

            new BaseProcessorTest()(proc, config, input, output)
        }
    }

    "ConvertToBigDecimal" must {
        "convert a field to BigDecimal" in {

            // Processor
            val proc = new ConvertToBigDecimal("")

            // Config
            val config = Json.obj("field" -> "key")

            // Input
            val input = List(DataPacket(List(
                    Map("key" -> 17),
                    Map("key" -> 1.337),
                    Map("key" -> 102341L),
                    Map("key" -> "1.3e12"),
                    Map("key" -> List(3, 183L, 1.337, "-1.2e-3"))
            )))

            //Expected output
            val output = List(DataPacket(List(
                    Map("key" -> BigDecimal(17)),
                    Map("key" -> BigDecimal(1.337)),
                    Map("key" -> BigDecimal(102341L)),
                    Map("key" -> BigDecimal("1.3e12")),
                    Map("key" -> List(BigDecimal(3), BigDecimal(183L), BigDecimal(1.337), BigDecimal("-1.2e-3")))
            )))

            new BaseProcessorTest()(proc, config, input, output)
        }
    }
}