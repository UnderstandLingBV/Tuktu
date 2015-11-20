package tuktu.processors

import java.io._
import java.lang.reflect.Method
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.Eval
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api._
import java.text.SimpleDateFormat
import tuktu.nosql.util.stringHandler
import tuktu.api.utils.evaluateTuktuString
import play.api.Logger

/**
 * Filters specific fields from the data tuple
 */
class FieldFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList = List[JsObject]()

    override def initialize(config: JsObject) {
        // Find out which fields we should extract
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            (for {
                fieldItem <- fieldList
                default = (fieldItem \ "default").asOpt[JsValue]
                fields = (fieldItem \ "path").as[List[String]]
                fieldName = evaluateTuktuString((fieldItem \ "result").as[String], datum)
                field = fields.head
                if (fields.size > 0 && datum.contains(field))
            } yield {
                fieldName -> utils.fieldParser(datum, fields, default)
            }).toMap
        })
    })
}

/**
 * Removes specific top-level fields from the each datum
 */
class FieldRemoveProcessor(resultName: String) extends BaseProcessor(resultName) {
    // The list of top-level fields to remove
    var fields: List[String] = _
    var ignoreEmptyDatums = false
    var ignoreEmptyDataPackets = false

    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]].getOrElse(Nil)
        ignoreEmptyDatums = (config \ "ignore_empty_datums").asOpt[Boolean].getOrElse(false)
        ignoreEmptyDataPackets = (config \ "ignore_empty_datapackets").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(
            (for (datum <- data.data) yield datum -- fields).filterNot(ignoreEmptyDatums && _.isEmpty))
    }) compose Enumeratee.filterNot((data: DataPacket) => ignoreEmptyDataPackets && data.data.isEmpty)
}

/**
 * Copies a path to resultName for each datum
 */
class FieldCopyProcessor(resultName: String) extends BaseProcessor(resultName) {
    var copyList: List[JsObject] = Nil

    override def initialize(config: JsObject) {
        copyList = (config \ "fields").asOpt[List[JsObject]].getOrElse(Nil)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            datum ++ (for {
                copy <- copyList

                path = (copy \ "path").as[List[String]]
                result = (copy \ "result").as[String]
            } yield {
                result -> utils.fieldParser(datum, path, null)
            })
        })
    })
}

/**
 * Adds a running count integer to data coming in
 */
class RunningCountProcessor(resultName: String) extends BaseProcessor(resultName) {
    var cnt = 0
    var perBlock = false
    var stepSize = 1

    override def initialize(config: JsObject) {
        cnt = (config \ "start_at").asOpt[Int].getOrElse(0)
        perBlock = (config \ "per_block").asOpt[Boolean].getOrElse(false)
        stepSize = (config \ "step_size").asOpt[Int].getOrElse(1)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        if (perBlock) {
            val res = new DataPacket(data.data.map(datum => datum + (resultName -> cnt)))
            cnt += stepSize
            res
        } else {
            new DataPacket(data.data.map(datum => {
                val r = datum + (resultName -> cnt)
                cnt += stepSize
                r
            }))
        }
    })
}

/**
 * Replaces one string for another (could be regex)
 */
class ReplaceProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""
    var sources = List[String]()
    var targets = List[String]()

    def replaceHelper(accum: String, offset: Int): String = {
        if (offset >= sources.size) accum
        else {
            // Replace in the accumulator and advance
            replaceHelper(accum.replaceAll(sources(offset), targets(offset)), offset + 1)
        }
    }

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        sources = (config \ "sources").as[List[String]]
        targets = (config \ "targets").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.map(datum => datum + (field -> {
            // Get field value to replace
            val value = datum(field).toString

            // Replace
            replaceHelper(value, 0)
        })))
    })
}

/**
 * Gets a JSON Object and fetches a single field to put it as top-level citizen of the data
 */
class JsonFetcherProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList = List[JsObject]()

    override def initialize(config: JsObject) {
        // Find out which fields we should extract
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            val newData = (for {
                fieldItem <- fieldList
                default = (fieldItem \ "default").asOpt[JsValue]
                fields = (fieldItem \ "path").as[List[String]]
                fieldName = (fieldItem \ "result").as[String]
                field = fields.head
                if (fields.size > 0 && datum.contains(field))
            } yield {
                fieldName -> utils.fieldParser(datum, fields, default)
            }).toMap

            datum ++ newData
        })
    })
}

/**
 * Renames a single field
 */
class FieldRenameProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList = List[JsObject]()

    override def initialize(config: JsObject) {
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {
            new DataPacket(for (datum <- data.data) yield {
                val mutableDatum = collection.mutable.Map[String, Any](datum.toSeq: _*)

                // A set of items that must be clean up at the end
                val cleanUp = collection.mutable.Set[String]()
                // A separate set of items that need to be preserved because they got recycled
                val dontCleanUp = collection.mutable.Set[String]()

                for {
                    field <- fieldList

                    fields = (field \ "path").as[List[String]]
                    result = (field \ "result").as[String]
                    f = fields.headOption.getOrElse("")
                    if (f.nonEmpty && datum.contains(f))
                } {
                    mutableDatum += result -> utils.fieldParser(datum, fields, null)
                    cleanUp += f
                    dontCleanUp += result
                }

                // Only remove items that should be removed
                mutableDatum --= cleanUp.diff(dontCleanUp)

                mutableDatum.toMap
            })
        }
    })
}

/**
 * Filters out data packets that satisfy a certain condition
 */
class PacketFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    /**
     * Evaluates a number of expressions
     */
    private def evaluateExpressions(datum: Map[String, Any], expressions: List[JsObject]): Boolean = {
        def evaluateExpression(expression: JsObject): Boolean = {
            // Get type, and/or and sub expressions, if any
            val exprType = (expression \ "type").as[String]
            val andOr = (expression \ "and_or").asOpt[String].getOrElse("and")
            val evalExpr = (expression \ "expression").as[JsValue]

            // See what the actual expression looks like
            evalExpr match {
                case e: JsString => {
                    // A real expression that we need to evaluate, based on the type of evaluation
                    exprType match {
                        case "groovy" => {
                            // Replace expression with values
                            val replacedExpression = evaluateTuktuString(e.value, datum)

                            try {
                                Eval.me(replacedExpression).asInstanceOf[Boolean]
                            } catch {
                                case e: Throwable => {
                                    Logger.error("Incorrect groovy expression: " + replacedExpression, e)
                                    true
                                }
                            }
                        }
                        case _ => {
                            // Replace expression with values
                            val replacedExpression = evaluateTuktuString(e.value, datum)
                            // Evaluate
                            // @TODO: Expand later using Scala parser combinators
                            val result = {
                                // Support for different operators; order is relevant
                                if (replacedExpression.contains("<=")) {
                                    val split = replacedExpression.split("<=").map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) <= split(i))
                                } else if (replacedExpression.contains(">=")) {
                                    val split = replacedExpression.split(">=").map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) >= split(i))
                                } else if (replacedExpression.contains("==")) {
                                    val split = replacedExpression.split("==").map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) == split(i))
                                } else if (replacedExpression.contains('=')) {
                                    val split = replacedExpression.split('=').map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) == split(i))
                                } else if (replacedExpression.contains('<')) {
                                    val split = replacedExpression.split('<').map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) < split(i))
                                } else if (replacedExpression.contains('>')) {
                                    val split = replacedExpression.split('>').map(_.trim)
                                    (1 until split.length).forall(i => split(i - 1) > split(i))
                                } else {
                                    Logger.warn("Redundant simple filter expression: " + replacedExpression)
                                    true
                                }
                            }

                            // Negate or not?
                            if (exprType == "negate") !result else result
                        }
                    }
                }
                case e: JsArray => {
                    // We have sub elements, process all of them
                    if (andOr == "or") e.as[List[JsObject]].exists(expr => evaluateExpression(expr))
                    else e.as[List[JsObject]].forall(expr => evaluateExpression(expr))
                }
                case _ => true
            }
        }

        // Go over all expressions and evaluate them
        expressions.exists(expr => evaluateExpression(expr))
    }

    var expressions: List[JsObject] = _
    var batch: Boolean = _
    var batchMinCount: Int = _

    override def initialize(config: JsObject) {
        expressions = (config \ "expressions").as[List[JsObject]]
        batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
        batchMinCount = (config \ "batch_min_count").asOpt[Int].getOrElse(1)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(
            // Check if we need to do batch or individual
            if (batch) {
                // Helper function to check if at least batchMinCount datums fulfill the expressions
                def helper(datums: List[Map[String, Any]], remaining: Int, matches: Int = 0): Boolean = {
                    if (matches >= batchMinCount) true
                    else if (matches + remaining < batchMinCount) false
                    else datums match {
                        case Nil => false
                        case datum :: tail => {
                            if (evaluateExpressions(datum, expressions)) helper(tail, remaining - 1, matches + 1)
                            else helper(tail, remaining - 1, matches)
                        }
                    }
                }
                // Check if we need to keep this DP in its entirety or not
                if (helper(data.data, data.data.size)) data.data
                else List()
            } else {
                // Filter data
                data.data.filter(datum => evaluateExpressions(datum, expressions))
            })
    }) compose Enumeratee.filter((data: DataPacket) => {
        data.data.nonEmpty
    })
}

/**
 * Adds a field with a constant (static) value
 */
class FieldConstantAdderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var value = ""
    var isNumeric = false

    override def initialize(config: JsObject) {
        value = (config \ "value").as[String]
        isNumeric = (config \ "is_numeric").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            if (!isNumeric)
                datum + (resultName -> evaluateTuktuString(value, datum))
            else
                datum + (resultName -> evaluateTuktuString(value, datum).toLong)
        })
    })
}

/**
 * Dumps the data to console
 */
class ConsoleWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var prettify = false

    override def initialize(config: JsObject) {
        prettify = (config \ "prettify").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        if (prettify) println(Json.prettyPrint(Json.toJson(data.data.map(datum => utils.anyMapToJson(datum)))))
        else println(data + "\r\n")

        data
    })
}

/**
 * Implodes an array of strings into a string
 */
class StringImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList = List[JsObject]()
    override def initialize(config: JsObject) {
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Find out which fields we should extract
            datum ++ (for (fieldObject <- fieldList) yield {
                // Get fields and separator
                val fields = (fieldObject \ "path").as[List[String]]
                val sep = (fieldObject \ "separator").as[String]
                // Get the array of strings
                val value = {
                    val someVal = utils.fieldParser(datum, fields, None)
                    if (someVal.isInstanceOf[JsValue])
                        someVal.asInstanceOf[JsValue].as[Traversable[String]]
                    else
                        someVal.asInstanceOf[Traversable[String]]
                }
                // Overwrite top-level field
                fields.head -> value.mkString(sep)
            })
        })
    })
}

/**
 * Implodes elements of a DataPacket into one element with a sequence
 */
class ImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _
    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(List((for (field <- fields) yield {
            field -> {
                for (datum <- data.data) yield datum(field)
            }
        }).toMap))
    })
}

/**
 * Implodes an array of JSON object-fields that contain strings at a subpath into a string
 */
class JsObjectImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList = List[JsObject]()

    override def initialize(config: JsObject) {
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Find out which fields we should extract
            datum ++ (for (fieldObject <- fieldList) yield {
                // Get fields
                val fields = (fieldObject \ "path").as[List[String]]
                val subpath = (fieldObject \ "subpath").as[List[String]]
                val sep = (fieldObject \ "separator").as[String]
                // Get the actual value
                val values = {
                    val arr = utils.fieldParser(datum, fields, None)
                    if (arr.isInstanceOf[JsValue])
                        arr.asInstanceOf[JsValue].as[Traversable[JsObject]]
                    else
                        Nil
                }
                // Now iterate over the objects
                val gluedValue = values.map(value => {
                    utils.jsonParser(value, subpath, None).as[JsString].value
                }).mkString(sep)
                // Replace top-level field
                fields.head -> gluedValue
            })
        })
    })
}

/**
 * Flattens a map object
 */
class FlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    def recursiveFlattener(mapping: Map[String, Any], currentKey: String, sep: String): Map[String, Any] = {
        // Get the values of the map
        (for ((key, value) <- mapping) yield {

            if (value.isInstanceOf[Map[String, Any]])
                // Get the sub fields recursively
                recursiveFlattener(value.asInstanceOf[Map[String, Any]], currentKey + sep + key, sep)
            else
                Map(currentKey + sep + key -> value)

        }).foldLeft(Map[String, Any]())(_ ++ _)
    }

    var fieldList = List[String]()
    var separator = ""

    override def initialize(config: JsObject) {
        // Get the field to flatten
        fieldList = (config \ "fields").as[List[String]]
        separator = (config \ "separator").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Set up mutable datum
            var mutableDatum = collection.mutable.Map(datum.toSeq: _*)

            // Find out which fields we should extract
            for (fieldName <- fieldList) {
                // Remove the fields we need to extract
                mutableDatum -= fieldName

                // Get the value
                val value = {
                    try {
                        recursiveFlattener(datum(fieldName).asInstanceOf[Map[String, Any]], fieldName, separator)
                    } catch {
                        case e: Exception => {
                            Logger.error("Unknown error occured", e)
                            Map[String, Any]()
                        }
                    }
                }

                // Replace
                mutableDatum ++= value
            }
            mutableDatum.toMap
        })
    })
}

/**
 * Takes a (JSON) sequence object and returns packets for each of the values in it
 */
class SequenceExploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""
    var ignoreEmpty = true

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        ignoreEmpty = (config \ "ignore_empty").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket((for (datum <- data.data) yield {
            // Get the field and explode it
            val values = datum(field).asInstanceOf[Seq[Any]]

            for (value <- values) yield datum + (field -> value)
        }).flatten)
    }) compose Enumeratee.filterNot((data: DataPacket) => ignoreEmpty && data.data.isEmpty)
}

/**
 * Splits a string up into a list of values based on a separator
 */
class StringSplitterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""
    var separator = ""
    var overwrite = false

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        separator = (config \ "separator").as[String]
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get the field and explode it
            val values = datum(field).toString.split(separator).toList

            datum + {
                if (overwrite) field -> values else resultName -> values
            }
        })
    })
}

/**
 * Assumes the data is a List[Map[_]] and gets one specific field from the map to remain in the list
 */
class ListMapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var listField = ""
    var mapField = ""
    var ignoreEmpty = true
    var overwrite = true

    override def initialize(config: JsObject) {
        listField = (config \ "list_field").as[String]
        mapField = (config \ "map_field").as[String]
        ignoreEmpty = (config \ "ignore_empty").asOpt[Boolean].getOrElse(true)
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get the list field's value
            val listValue = datum(listField).asInstanceOf[List[Map[String, Any]]]

            // Get the actual fields of the maps iteratively
            val newList = listValue.map(listItem => {
                // Get map field
                listItem(mapField)
            })

            // Return new list rather than old
            if (overwrite)
                datum + (listField -> newList)
            else
                datum + (resultName -> newList)
        })
    }) compose Enumeratee.filter((data: DataPacket) => { if (ignoreEmpty) !data.data.isEmpty else true })
}

/**
 * Assumes the data is a List[Map[_]] and gets specific fields from the map to remain in the list
 */
class MultiListMapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var listField = ""
    var mapFields: List[String] = _
    var ignoreEmpty = true
    var overwrite = true

    override def initialize(config: JsObject) {
        listField = (config \ "list_field").as[String]
        mapFields = (config \ "map_fields").as[List[String]]
        ignoreEmpty = (config \ "ignore_empty").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get the list field's value
            val listValue = datum(listField).asInstanceOf[List[Map[String, Any]]]

            // Keep map of results
            val resultMap = collection.mutable.Map[String, collection.mutable.ListBuffer[Any]]()

            // Get the actual fields of the maps iteratively
            listValue.map(listItem => {
                mapFields.foreach(field => {
                    // Add to our resultMap
                    if (!resultMap.contains(field))
                        resultMap += field -> collection.mutable.ListBuffer[Any]()

                    resultMap(field) += listItem(field)
                })
            })

            // Add to our total result
            datum -- mapFields ++ resultMap.map(elem => elem._1 -> elem._2.toList)
        })
    }) compose Enumeratee.filter((data: DataPacket) => { if (ignoreEmpty) !data.data.isEmpty else true })
}

/**
 * Verifies all fields are present before sending it on
 */
class ContainsAllFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldContainingList: String = _
    var field: String = _
    var containsField = ""

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        containsField = (config \ "contains_field").as[String]
        fieldContainingList = (config \ "field_list").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket({
            val resultMap = (for (datum <- data.data) yield {
                // Build the actual set of contain-values
                val containsSet = collection.mutable.Set[Any]() ++ datum(containsField).asInstanceOf[Seq[Any]]

                // Get our record
                val record = datum(fieldContainingList).asInstanceOf[List[Map[String, Any]]]

                // Do the matching
                for (rec <- record if !containsSet.isEmpty)
                    containsSet -= rec(field)

                if (containsSet.isEmpty) datum
                else Map[String, Any]()
            })

            val filteredMap = resultMap.filter(elem => !elem.isEmpty)
            filteredMap
        })
    }) compose Enumeratee.filter((data: DataPacket) => !data.data.isEmpty)
}

/**
 * Takes a Map[String, Any] and makes it a top-level citizen
 */
class MapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get map
            val map = datum(field).asInstanceOf[Map[String, Any]]

            // Add to total
            datum ++ map
        })
    })
}

/**
 * Sends a DataPacket's content to an Akka actor given by an actor path
 */
class AkkaSenderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var actor_path = ""

    override def initialize(config: JsObject) {
        actor_path = (config \ "actor_path").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val selection = Akka.system.actorSelection(actor_path)
        selection ! data.data
        data
    })
}