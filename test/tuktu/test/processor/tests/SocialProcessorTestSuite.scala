package tuktu.test.processor.tests

import org.scalatest.DoNotDiscover
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec

import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api.DataPacket
import tuktu.social.processors._
import tuktu.test.processor.BaseProcessorTest
import org.scalatest.BeforeAndAfter
import play.api.libs.concurrent.Akka
import play.api.Play

@DoNotDiscover
class SocialProcessorTestSuite extends PlaySpec {

    "TwitterTaggerProcessor" must {

        val twitter1 = Json.parse("""
            {
                "entities" : {
                    "urls" : [],
                    "hashtags" : [],
                    "user_mentions" : [ 
                        {
                            "screen_name" : "screen1",
                            "id_str" : "1",
                            "name" : "name1",
                            "id" : 1
                        },
                        {
                            "screen_name" : "screen2",
                            "id_str" : "2",
                            "name" : "word2",
                            "id" : 2
                        }
                    ],
                    "symbols" : []
                },
                "in_reply_to_user_id" : 1,
                "in_reply_to_user_id_str" : "1",
                "in_reply_to_screen_name" : "screen1",
                "text" : "word1 word2 word3 word4",
                "user" : {
                    "screen_name" : "screen0",
                    "id_str" : "0",
                    "id" : 0,
                    "name" : "name0"
                }
            }""")

        val twitter2 = Json.parse("""
            {
                "entities" : {
                    "urls" : [],
                    "hashtags" : [],
                    "user_mentions" : [ 
                        {
                            "screen_name" : "screen3",
                            "id_str" : "3",
                            "name" : "name3",
                            "id" : 3
                        },
                        {
                            "screen_name" : "screen4",
                            "id_str" : "4",
                            "name" : "name4",
                            "id" : 4
                        }
                    ],
                    "symbols" : []
                },
                "in_reply_to_user_id" : 3,
                "in_reply_to_user_id_str" : "3",
                "in_reply_to_screen_name" : "screen3",
                "text" : "some random stuff we're not going to match to see if filtering works",
                "user" : {
                    "screen_name" : "screen5",
                    "id_str" : "5",
                    "id" : 5,
                    "name" : "name5"
                }
            }""")

        // Processor
        val proc = new TwitterTaggerProcessor("tags")

        // Input
        val input = List(DataPacket(List(
            Map("" -> twitter1),
            Map("" -> twitter2))))

        "extract users and keywords correctly; no exclude, no combined, no userTagField" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(
                    "keywords" -> List("word2"),
                    "users" -> List("2")),
                "exclude_on_none" -> false,
                "combined" -> false)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> Map("users" -> List("2"), "keywords" -> List("word2"))),
                        Map("" -> twitter2, "tags" -> Map("users" -> List(), "keywords" -> List())))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "exclude users with no hits" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(
                    "keywords" -> List("word2"),
                    "users" -> List("2")),
                "exclude_on_none" -> true,
                "combined" -> false)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> Map("users" -> List("2"), "keywords" -> List("word2"))))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "combine correctly" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(
                    "keywords" -> List("word2"),
                    "users" -> List("2")),
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> List("2", "word2")))),
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> List("word2", "2")))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "get the user tag field's value" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "user_tag_field" -> "name",
                "tags" -> Json.obj(
                    "keywords" -> List("word2"),
                    "users" -> List("2")),
                "exclude_on_none" -> true,
                "combined" -> false)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> Map("users" -> List("word2"), "keywords" -> List("word2"))))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "only keep distinct matches" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "user_tag_field" -> "name",
                "tags" -> Json.obj(
                    "keywords" -> List("word2"),
                    "users" -> List("2")),
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> List("word2")))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "default to sender's name if no users were specified" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "user_tag_field" -> "name",
                "tags" -> Json.obj(),
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> twitter1, "tags" -> List("name0")),
                        Map("" -> twitter2, "tags" -> List("name5")))))

            new BaseProcessorTest()(proc, config, input, output)
        }

    }

    "FacebookTaggerProcessor" must {

        val facebook1 = Json.parse("""
            {
                "from" : {
                    "name" : "name 1",
                    "id" : "id1",
                    "first_name" : "first name",
                    "last_name" : "last name 1"
                },
                "to" : {
                    "data" : [ 
                        {
                            "name" : "name 1",
                            "id" : "id3"
                        },
                        {
                            "name" : "name4",
                            "id" : "id4"
                        }
                    ]
                }
            }""")

        val facebook2 = Json.parse("""
            {
                "from" : {
                    "name" : "first name 2 last name 2",
                    "id" : "id2",
                    "first_name" : "first name",
                    "last_name" : "last name 2"
                },
                "to" : {
                    "data" : [ 
                        {
                            "name" : "name3",
                            "id" : "id3"
                        },
                        {
                            "name" : "name5",
                            "id" : "id5"
                        }
                    ]
                }
            }""")

        // Processor
        val proc = new FacebookTaggerProcessor("tags")

        // Input
        val input = List(DataPacket(List(
            Map("" -> facebook1),
            Map("" -> facebook2))))

        "extract users correctly; no exclude, no combined, no userTagField" in {
            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id2", "id3")),
                    "exclude_on_none" -> false,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id3"))),
                            Map("" -> facebook2, "tags" -> Map("users" -> List("id2", "id3"))))),
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id3"))),
                            Map("" -> facebook2, "tags" -> Map("users" -> List("id3", "id2"))))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id4")),
                    "exclude_on_none" -> false,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id4"))),
                            Map("" -> facebook2, "tags" -> Map("users" -> List())))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("name5")),
                    "exclude_on_none" -> false,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List())),
                            Map("" -> facebook2, "tags" -> Map("users" -> List("name5"))))))

                new BaseProcessorTest()(proc, config, input, output)
            }
        }

        "exclude users with no hits" in {
            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id2", "id3")),
                    "exclude_on_none" -> true,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id3"))),
                            Map("" -> facebook2, "tags" -> Map("users" -> List("id2", "id3"))))),
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id3"))),
                            Map("" -> facebook2, "tags" -> Map("users" -> List("id3", "id2"))))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id4")),
                    "exclude_on_none" -> true,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> Map("users" -> List("id4"))))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("name5")),
                    "exclude_on_none" -> true,
                    "combined" -> false)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook2, "tags" -> Map("users" -> List("name5"))))))

                new BaseProcessorTest()(proc, config, input, output)
            }
        }

        "combine correctly" in {
            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id2", "id3")),
                    "exclude_on_none" -> true,
                    "combined" -> true)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> List("id3")),
                            Map("" -> facebook2, "tags" -> List("id2", "id3")))),
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> List("id3")),
                            Map("" -> facebook2, "tags" -> List("id3", "id2")))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("id4")),
                    "exclude_on_none" -> true,
                    "combined" -> true)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook1, "tags" -> List("id4")))))

                new BaseProcessorTest()(proc, config, input, output)
            }

            {
                // Config
                val config = Json.obj(
                    "object_field" -> "",
                    "tags" -> Json.obj(
                        "users" -> List("name5")),
                    "exclude_on_none" -> true,
                    "combined" -> true)

                // Expected output
                val output = List(
                    DataPacket(
                        List(
                            Map("" -> facebook2, "tags" -> List("name5")))))

                new BaseProcessorTest()(proc, config, input, output)
            }
        }

        "only keep distinct matches" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(
                    "users" -> List("name 1")),
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> facebook1, "tags" -> List("name 1")))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "get the user tag field's value" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(
                    "users" -> List("id1", "name 1")),
                "user_tag_field" -> "name",
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> facebook1, "tags" -> List("name 1")))))

            new BaseProcessorTest()(proc, config, input, output)
        }

        "default to sender's name if no users were specified" in {
            // Config
            val config = Json.obj(
                "object_field" -> "",
                "tags" -> Json.obj(),
                "exclude_on_none" -> true,
                "combined" -> true)

            // Expected output
            val output = List(
                DataPacket(
                    List(
                        Map("" -> facebook1, "tags" -> List("name 1")),
                        Map("" -> facebook2, "tags" -> List("first name 2 last name 2")))))

            new BaseProcessorTest()(proc, config, input, output)
        }
    }
}