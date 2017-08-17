package tuktu.processors

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationLong
import scala.collection.GenTraversableOnce
import scala.util.Try
import akka.actor._
import akka.pattern.ask
import groovy.util.Eval
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.Logger
import tuktu.api._
import tuktu.api.Parsing._
import tuktu.api.utils.evaluateTuktuString

/**
 * Doesn't do anything
 */
class SkipProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        data
    })
}

/**
 * Adds a delay between two data packets
 */
class DelayActor(delay: Long) extends Actor with ActorLogging {
    // Timestamp of when the next message can be sent
    var nextMessage: Long = System.currentTimeMillis

    def receive = {
        case "" =>
            val now: Long = System.currentTimeMillis
            if (now >= nextMessage) {
                // If timestamp of next message to be sent is before now, send immediately
                sender ! ""
                nextMessage = now + delay
            } else {
                // Otherwise schedule to send response as soon as next message can be sent
                Akka.system.scheduler.scheduleOnce(
                    (nextMessage - now) milliseconds,
                    sender,
                    "")
                nextMessage = nextMessage + delay
            }

        case sp: StopPacket =>
            self ! PoisonPill
    }
}
class DelayProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = akka.util.Timeout(100 days)
    var actor: ActorRef = _

    override def initialize(config: JsObject) {
        val delay = (config \ "delay").as[Long]
        actor = Akka.system.actorOf(Props(classOf[DelayActor], delay))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        Await.ready(actor ? "", timeout.duration)
        data
    }) compose Enumeratee.onEOF { () => actor ! new StopPacket }
}

/**
 * Counts the number of EOFs and prints them to info when done
 */
class CountEOFProcessor(resultName: String) extends BaseProcessor(resultName) {
    var dataSeen = 0
    var datumSeen = 0
    var eofCounts = 0

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        datumSeen += data.size
        dataSeen += 1
        data
    }) compose Enumeratee.onEOF { () =>
        eofCounts = eofCounts + 1
        Logger.info(resultName + " got " + eofCounts + " EOFs after " + datumSeen + " datums, in " + dataSeen + " DataPackets.")
    }
}

/**
 * Gets the head of a list of one of the DataPacket's elements
 */
class HeadOfListProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var keepOriginal: Boolean = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        keepOriginal = (config \ "keep_original_field").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val value = datum(field).asInstanceOf[Seq[Any]]
            if (value.nonEmpty)
                datum + (resultName -> value.head)
            else if (!keepOriginal)
                datum - resultName
            else datum
        }
    })
}

/**
 * Filters specific fields from the data tuple
 */
class FieldFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var conf: utils.PreparedJsObject = _

    override def initialize(config: JsObject) {
        conf = utils.prepareTuktuJsObject(config)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            (for {
                fieldItem <- (conf.evaluate(datum) \ "fields").as[JsArray].value
                default = (fieldItem \ "default").asOpt[JsValue]
                fields = (fieldItem \ "path").as[List[String]]
                fieldName = (fieldItem \ "result").as[String]
            } yield {
                fieldName -> utils.fieldParser(datum, fields).getOrElse(default.get)
            }).toMap
        }
    })
}

/**
 * Removes specific top-level fields from each datum
 */
class FieldRemoveProcessor(resultName: String) extends BaseProcessor(resultName) {
    // The list of top-level fields to remove
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]].getOrElse(Nil)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        (for (datum <- data) yield datum -- fields)
    })
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
        for (datum <- data) yield {
            datum ++ (for {
                copy <- copyList

                path = (copy \ "path").as[List[String]]
                result = (copy \ "result").as[String]
            } yield {
                result -> utils.fieldParser(datum, path).getOrElse(null)
            })
        }
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
            val res = for (datum <- data) yield datum + (resultName -> cnt)
            cnt += stepSize
            res
        } else {
            for (datum <- data) yield {
                val r = datum + (resultName -> cnt)
                cnt += stepSize
                r
            }
        }
    })
}

/**
 * Replaces one string for another (could be regex)
 */
class ReplaceProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var replacements: Seq[(String, String)] = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        replacements = (config \ "sources").as[Seq[String]].zip((config \ "targets").as[Seq[String]])
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield datum +
            (resultName -> replacements.foldLeft(utils.fieldParser(datum, field).get match {
                case js: JsString => js.value
                case a            => a.toString
            }) { case (accum, (source, target)) => accum.replaceAll(source, target) })
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
        for (datum <- data) yield {
            val newData = (for {
                fieldItem <- fieldList
                default = (fieldItem \ "default").asOpt[JsValue]
                fields = {
                    val p = (fieldItem \ "path").as[List[String]]
                    if (p.size == 1)
                        utils.evaluateTuktuString(p.head, datum).split('.').toList
                    else
                        p.map(utils.evaluateTuktuString(_, datum))
                }
                fieldName = (fieldItem \ "result").as[String]
                field = fields.head
            } yield {
                fieldName -> utils.fieldParser(datum, fields).getOrElse(utils.evaluateTuktuJsValue(default.get, datum))
            })

            datum ++ newData
        }
    })
}

/**
 * Gets a list of JSON Objects and fetches a single field to put it as top-level citizen of the data
 */
class ListJsonFetcherProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var default: Option[JsValue] = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        default = (config \ "default").asOpt[JsValue]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the field and paths
            val paths = datum(field).asInstanceOf[Seq[String]]
            // Get the JSON values
            datum ++ paths.map { path =>
                path -> utils.fieldParser(datum, path.split('.').toList).getOrElse(default.get)
            }
        }
    })
}

/**
 * Renames a single field
 */
class FieldRenameProcessor(resultName: String) extends BaseProcessor(resultName) {
    var conf: utils.PreparedJsObject = _

    override def initialize(config: JsObject) {
        conf = utils.prepareTuktuJsObject(config)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val mutableDatum = collection.mutable.Map[String, Any](datum.toSeq: _*)

            // A set of items that must be clean up at the end
            val cleanUp = collection.mutable.Set[String]()
            // A separate set of items that need to be preserved because they got recycled
            val dontCleanUp = collection.mutable.Set[String]()

            for {
                field <- (conf.evaluate(datum) \ "fields").as[JsArray].value

                fields = (field \ "path").as[List[String]]
                result = (field \ "result").as[String]
                f = fields.headOption.getOrElse("")
                if (f.nonEmpty && datum.contains(f))
            } {
                mutableDatum += result -> utils.fieldParser(datum, fields).getOrElse(null)
                cleanUp += f
                dontCleanUp += result
            }

            // Only remove items that should be removed
            mutableDatum --= cleanUp.diff(dontCleanUp)

            mutableDatum.toMap
        }
    })
}

/**
 * Evaluates nested Tuktu expressions in a string until no more exist
 */
class EvaluateNestedTuktuExpressionsProcessor(resultName: String) extends BaseProcessor(resultName) {
    var expr: evaluateTuktuString.TuktuStringRoot = _
    override def initialize(config: JsObject) {
        expr = evaluateTuktuString.prepare((config \ "expression").as[String])
    }

    def evalHelper(string: String, datum: Map[String, Any]): String = {
        val newString = utils.evaluateTuktuString(string, datum)
        if (newString != string)
            evalHelper(newString, datum)
        else newString
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> evalHelper(expr.evaluate(datum), datum))
        }
    })
}

/**
 * Parses a predicate and adds its evaluation (true/false) to the DP
 */
class PredicateProcessor(resultName: String) extends BaseProcessor(resultName) {
    var predicate: evaluateTuktuString.TuktuStringRoot = _

    override def initialize(config: JsObject) {
        predicate = evaluateTuktuString.prepare((config \ "predicate").as[String])
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the predicate parser
            val p = PredicateParser(predicate.evaluate(datum), datum)
            datum + (resultName -> p)
        }
    })
}

/**
 * Filters out data packets that satisfy a certain condition
 */
class PacketFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    /**
     * Evaluates an expression
     */
    def evaluateExpression(datum: Map[String, Any]): Boolean = {
        expressionType match {
            case "groovy" =>
                try {
                    Eval.me(expression.evaluate(datum)).asInstanceOf[Boolean]
                } catch {
                    case e: Throwable =>
                        warningsSinceLast += 1
                        // Only show at most one warning per 0.1s
                        if (System.currentTimeMillis - 100 > lastWarning) {
                            Logger.warn("Could not evaluate " + warningsSinceLast + " groovy expressions since last warning of this type in this processor.\nThe last expression that failed was:\n" + expression + "\n" + "Defaulting to: " + default.getOrElse(true), e)
                            lastWarning = System.currentTimeMillis
                            warningsSinceLast = 0
                        }
                        default.getOrElse(true)
                }

            case _ =>
                val result: Boolean = try {
                    if (constant.isDefined)
                        evaluatedAndParsedExpression.get.get.evaluate(datum)
                    else
                        PredicateParser(expression.evaluate(datum), datum)
                } catch {
                    case e: Throwable =>
                        warningsSinceLast += 1
                        // Only show at most one warning per 0.1s
                        if (System.currentTimeMillis - 100 > lastWarning) {
                            Logger.warn("Could not evaluate " + warningsSinceLast + " Tuktu Predicate expressions since last warning of this type in this processor.\nThe last expression that failed was:\n" + expression + "\n" + "Defaulting to: " + default, e)
                            lastWarning = System.currentTimeMillis
                            warningsSinceLast = 0
                        }
                        default.get
                }

                // Negate or not?
                if (expressionType == "negate") !result else result
        }
    }

    var lastWarning: Long = 0
    var warningsSinceLast: Int = 0

    var expressionType: String = _
    var expression: evaluateTuktuString.TuktuStringRoot = _
    var evaluatedAndParsedExpression: Option[Try[PredicateParser.BooleanNode]] = None
    var evaluate: Boolean = _
    var constant: Option[String] = _
    var default: Option[Boolean] = _
    var batch: Boolean = _
    var batchMinCount: Int = _
    var filterEmpty: Boolean = _

    override def initialize(config: JsObject) {
        expressionType = (config \ "type").as[String]
        evaluate = (config \ "evaluate").asOpt[Boolean].getOrElse(true)
        constant = (config \ "constant").asOpt[String].flatMap {
            case _ if evaluate == false               => Some("global")
            case "global" | "'global'" | "\"global\"" => Some("global")
            case "local" | "'local'" | "\"local\""    => Some("local")
            case _                                    => None
        }
        expression = expressionType match {
            case "groovy"      => evaluateTuktuString.prepare((config \ "expression").as[String])
            case _ if evaluate => evaluateTuktuString.prepare((config \ "expression").as[String])
            case _ if !evaluate =>
                val expr = (config \ "expression").as[String]
                // If we don't need to evaluate it, we can prepare the predicate expression already
                evaluatedAndParsedExpression = Some(Try(PredicateParser.prepare(expr)))
                evaluateTuktuString.TuktuStringRoot(Seq(evaluateTuktuString.TuktuStringString(expr)))
        }

        default = (config \ "default") match {
            case b: JsBoolean => Some(b.value)
            case s: JsString  => Some(s.value.toLowerCase.replaceAll("[^a-z]", "").toBoolean)
            case _            => None
        }
        batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
        batchMinCount = (config \ "batch_min_count").asOpt[Int].getOrElse(1)
        filterEmpty = (config \ "filter_empty").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // If we need to evaluate a non-groovy expression, which isn't already prepared (because it's global or static), and is some form of constant, and the DP is not empty,
        // then use the first datum to prepare the predicate expression
        if (evaluate == true && expressionType != "groovy" && evaluatedAndParsedExpression.isEmpty && constant.isDefined && data.nonEmpty)
            evaluatedAndParsedExpression = Some(Try(PredicateParser.prepare(expression.evaluate(data.data.head))))

        val result = DataPacket(
            // Check if we need to do batch or individual
            if (batch) {
                // Helper function to check if at least batchMinCount datums fulfill the expressions
                def helper(datums: List[Map[String, Any]], remaining: Int, matches: Int = 0): Boolean = {
                    if (matches >= batchMinCount) true
                    else if (matches + remaining < batchMinCount) false
                    else datums match {
                        case Nil => false
                        case datum :: tail =>
                            if (evaluateExpression(datum))
                                helper(tail, remaining - 1, matches + 1)
                            else
                                helper(tail, remaining - 1, matches)
                    }
                }
                // Check if we need to keep this DP in its entirety or not
                if (helper(data.data, data.size)) data.data
                else List()
            } else {
                // Filter data
                data.data.filter(datum => evaluateExpression(datum))
            })

        // If it's only locally constant, reset it to None so that it will be set by the next DP anew
        if (constant == Some("local"))
            evaluatedAndParsedExpression = None

        result
    })
}

/**
 * Filters out data packets that satisfy a certain condition
 */
class PacketRegexFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    /**
     * Evaluates a number of expressions
     */
    private def evaluateExpressions(datum: Map[String, Any], expressions: List[JsObject]): Boolean = {
        def evaluateExpression(expression: JsObject): Boolean = {
            // Get type, and/or and sub expressions, if any
            val exprType = (expression \ "type").as[String]
            val andOr = (expression \ "and_or").asOpt[String].getOrElse("and")
            val evalExpr = (expression \ "expression").as[JsValue]
            val field = (expression \ "field").as[String]

            // See what the actual expression looks like
            evalExpr match {
                case e: JsString => {
                    val replacedExpression = evaluateTuktuString(e.value, datum).r

                    val find = replacedExpression.findFirstIn(datum(field).asInstanceOf[String]) != None

                    if (exprType == "negate") !find else find
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
        DataPacket(
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
                if (helper(data.data, data.size)) data.data
                else List()
            } else {
                // Filter data
                data.data.filter(datum => evaluateExpressions(datum, expressions))
            })
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
        for (datum <- data) yield {
            if (!isNumeric)
                datum + (evaluateTuktuString(resultName, datum) -> evaluateTuktuString(value, datum))
            else
                datum + (evaluateTuktuString(resultName, datum) -> evaluateTuktuString(value, datum).toLong)
        }
    })
}

/**
 * Merges the specified fields into one data packet.
 */
class DataPacketFieldMergerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var value = ""
    var isNumeric = false
    var isDecimal = false
    var batch: Boolean = _
    var fieldList = List[JsObject]()

    override def initialize(config: JsObject) {
        value = (config \ "value").as[String]
        isNumeric = (config \ "is_numeric").asOpt[Boolean].getOrElse(false)
        isDecimal = (config \ "is_decimal").asOpt[Boolean].getOrElse(false)
        batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val maps = for (datum <- data.data) yield {
            val map = (for {
                fieldItem <- fieldList
                default = (fieldItem \ "default").asOpt[JsValue]
                fields = (fieldItem \ "path").as[List[String]]
                fieldName = evaluateTuktuString((fieldItem \ "result").as[String], datum)
                field = fields.head
            } yield {
                fieldName -> utils.fieldParser(datum, fields).getOrElse(default.get)
            }).toMap

            val map2 = Map(evaluateTuktuString(resultName, datum) -> {
                if (!isNumeric && !isDecimal) evaluateTuktuString(value, datum)
                else if (isNumeric) evaluateTuktuString(value, datum).toLong
                else evaluateTuktuString(value, datum).toDouble
            })

            map ++ map2
        }

        // If it is a batch, we merge the individual maps into one datum
        if (batch) DataPacket(List(maps.flatten.toMap))
        else DataPacket(maps)
    })
}

/**
 * Dumps the data to console
 */
class ConsoleWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var prettify: Boolean = _
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        prettify = (config \ "prettify").asOpt[Boolean].getOrElse(false)
        fields = (config \ "fields").asOpt[List[String]].getOrElse(Nil)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val filtered = fields match {
            case Nil => data
            case _   => data.map(datum => datum.filterKeys { key => fields.contains(key) })
        }

        if (prettify)
            println(Json.prettyPrint(Json.toJson(filtered.data.map(datum => utils.MapToJsObject(datum)))))
        else
            println(filtered + "\r\n")

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
        for (datum <- data) yield {
            // Find out which fields we should extract
            datum ++ (for (fieldObject <- fieldList) yield {
                // Get fields and separator
                val fields = (fieldObject \ "path").as[List[String]]
                val sep = (fieldObject \ "separator").as[String]
                // Get the array of strings
                val value = {
                    val someVal = utils.fieldParser(datum, fields).get
                    someVal match {
                        case sl: JsValue       => sl.as[Traversable[String]]
                        case sl: Array[String] => sl.toTraversable
                        case sl: Array[Any]    => sl.map(_.toString).toTraversable
                        case _                 => someVal.asInstanceOf[Traversable[String]]
                    }
                }
                // Overwrite top-level field
                fields.head -> value.mkString(sep)
            })
        }
    })
}

/**
 * Implodes a list of tuples into a string
 */
class TupleListStringImploder(resultName: String) extends BaseProcessor(resultName) {
    var fieldList: List[JsObject] = _
    override def initialize(config: JsObject) {
        fieldList = (config \ "fields").as[List[JsObject]]
    }

    def doConversion(elem: List[Any], sep: String) = {
        elem.map(el => el match {
            case b: (Any, Any)      => b._1.toString + sep + b._2.toString
            case b: (Any, Any, Any) => b._1.toString + sep + b._2.toString + sep + b._3.toString
            case b: (Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep + b._3.toString +
                sep + b._4.toString
            case b: (Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep + b._3.toString +
                sep + b._4.toString + sep + b._5.toString
            case b: (Any, Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep + b._3.toString +
                sep + b._4.toString + sep + b._5.toString + sep + b._6.toString
            case b: (Any, Any, Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep + b._3.toString +
                sep + b._4.toString + sep + b._5.toString + sep + b._6.toString + sep + b._7.toString
            case b: (Any, Any, Any, Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep +
                b._3.toString + sep + b._4.toString + sep + b._5.toString + sep + b._6.toString + sep + b._7.toString +
                sep + b._8.toString
            case b: (Any, Any, Any, Any, Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep +
                b._3.toString + sep + b._4.toString + sep + b._5.toString + sep + b._6.toString + sep + b._7.toString +
                sep + b._8.toString + sep + b._9.toString
            // Should we ever need more?
            case b: (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => b._1.toString + sep + b._2.toString + sep +
                b._3.toString + sep + b._4.toString + sep + b._5.toString + sep + b._6.toString + sep + b._7.toString +
                sep + b._8.toString + sep + b._9.toString + sep + b._10.toString
        })
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Find out which fields we should extract
            datum ++ (for (fieldObject <- fieldList) yield {
                // Get fields and separator
                val fields = (fieldObject \ "path").as[List[String]]
                val sep = (fieldObject \ "separator").as[String]
                // Get the tuples
                val value = {
                    val someVal = utils.fieldParser(datum, fields).get
                    someVal match {
                        case a: (Any, Any)    => a._1.toString + sep + a._2.toString
                        case a: Seq[_]        => doConversion(a.toList, sep)
                        case a: Map[Any, Any] => doConversion(a.toList, sep)
                        case a: Iterable[_]   => doConversion(a.toList, sep)
                        case a: Any           => a
                    }
                }
                // Overwrite top-level field
                fields.head -> value
            })
        }
    })
}

/**
 * Implodes a number of fields (or an entire DataPacket) into a sequence
 */
class ImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: Option[List[String]] = _
    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            fields match {
                case Some(fs) => datum + (resultName -> fs.flatMap(field => {
                    datum(field) match {
                        case f: Seq[Any] => f.toList
                        case f: Array[Any] => f.toList
                        case f: Any => List(f)
                    }
                }))
                case None     => datum + (resultName -> datum.toList.sortBy(_._1).map(_._2))
            }
        }
    })
}

/**
 * Implodes elements of a DataPacket into one element with a sequence based on key
 */
class KeyImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _
    var merge: Boolean = _
    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
        merge = (config \ "merge").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        DataPacket(List(
            (if (merge) data.data.headOption.getOrElse(Map.empty) else Map.empty[String, Any]) ++
                (for (field <- fields) yield {
                    field -> {
                        for (datum <- data.data) yield utils.fieldParser(datum, field).get
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
        for (datum <- data) yield {
            // Find out which fields we should extract
            datum ++ (for (fieldObject <- fieldList) yield {
                // Get fields
                val fields = (fieldObject \ "path").as[List[String]]
                val subpath = (fieldObject \ "subpath").as[List[String]]
                val sep = (fieldObject \ "separator").as[String]
                // Get the actual value
                val values = {
                    val arr = utils.fieldParser(datum, fields).get
                    if (arr.isInstanceOf[JsValue])
                        arr.asInstanceOf[JsValue].as[Traversable[JsObject]]
                    else
                        Nil
                }
                // Now iterate over the objects
                val gluedValue = values.map(value => {
                    utils.jsonParser(value, subpath).get.as[JsString].value
                }).mkString(sep)
                // Replace top-level field
                fields.head -> gluedValue
            })
        }
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
        for (datum <- data) yield {
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
        }
    })
}

/**
 * Takes a sequence object and returns packets for each of the values in it
 */
class SequenceExploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.flatMap { datum =>
            // Get the field and explode it
            val values: Seq[Any] = datum(field) match {
                case js: JsArray => js.value
                case any: Any    => any.asInstanceOf[Seq[Any]]
            }

            for (value <- values) yield datum + (field -> value)
        }
    })
}

/**
 * Takes a sequence of sequence objects and flattens it
 */
class SequenceFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.map { datum =>
            // Get the field and explode it
            val values: Seq[Any] = utils.fieldParser(datum, field).get.asInstanceOf[Seq[Seq[Any]]].flatten

            datum + (resultName -> values)
        }
    })
}

/**
 * Takes a sequence and returns a sequence of distinct values
 */
class DistinctSequenceProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: evaluateTuktuString.TuktuStringRoot = _

    override def initialize(config: JsObject) {
        field = evaluateTuktuString.prepare((config \ "field").as[String])
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Get the field and explode it
            val values = utils.fieldParser(datum, field.evaluate(datum)).get.asInstanceOf[Seq[_]]

            datum + (resultName -> values.distinct)
        }
    })
}

/**
 * Wraps either the whole list of Datums of a DataPacket under a new result name as a whole, or each datum under a new result name separately
 */
class DataPacketWrapperProcessor(resultName: String) extends BaseProcessor(resultName) {
    var as_whole: Boolean = _

    override def initialize(config: JsObject) {
        as_whole = (config \ "as_whole").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        if (as_whole)
            DataPacket(List(Map(resultName -> data.data)))
        else
            data.map(datum => Map(resultName -> datum))
    })
}

/**
 * Splits a string up into a list of values based on a separator
 */
class StringSplitterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var separator: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        separator = (config \ "separator").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the field and explode it
            val values = datum(field).toString.split(separator).toList

            datum + (resultName -> values)
        }
    })
}

/**
 * Assumes the data is a List[Map[_]] and gets one specific field from the map to remain in the list
 */
class ListMapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var listField: String = _
    var mapField: String = _

    override def initialize(config: JsObject) {
        listField = (config \ "list_field").as[String]
        mapField = (config \ "map_field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Get the list field's value
            val listValue = datum(listField) match {
                case lv: JsArray   => lv.value.toList
                case lv: List[Any] => lv
            }

            // Get the actual fields of the maps iteratively
            val newList = listValue.map(listItem => {
                // Get map field
                listItem match {
                    case mf: JsObject         => (mf \ mapField)
                    case mf: Map[String, Any] => mf(mapField)
                }
            })

            datum + (resultName -> newList)
        }
    })
}

/**
 * Assumes the data is a List[Map[_]] and gets specific fields from the map to remain in the list
 */
class MultiListMapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var listField: String = _
    var mapFields: List[String] = _

    override def initialize(config: JsObject) {
        listField = (config \ "list_field").as[String]
        mapFields = (config \ "map_fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Get the list field's value
            val q = datum(listField)
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
            datum ++ resultMap.map(elem => elem._1 -> elem._2.toList).toMap
        }
    })
}

/**
 * Verifies all fields are present before sending it on
 */
class ContainsAllFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldContainingList: String = _
    var field: String = _
    var containsField: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        containsField = (config \ "contains_field").as[String]
        fieldContainingList = (config \ "field_list").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        (for (datum <- data) yield {
            // Build the actual set of contain-values
            val containsSet = collection.mutable.Set[Any](datum(containsField).asInstanceOf[Seq[Any]]: _*)

            // Get our record
            val record = datum(fieldContainingList).asInstanceOf[List[Map[String, Any]]]

            // Do the matching
            for (rec <- record if containsSet.nonEmpty)
                containsSet -= rec(field)

            if (containsSet.isEmpty) datum
            else Map[String, Any]()
        }).filter(_.nonEmpty)
    })
}

/**
 * Takes a Map[String, Any] and makes it a top-level citizen
 */
class MapFlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Get map
            val map = datum(field) match {
                case a: Map[String, Any] => a
                case a: JsObject         => utils.JsObjectToMap(a)
            }

            // Add to total
            datum ++ map
        }
    })
}

/**
 * Sends a DataPacket's content to an Akka actor given by an actor path
 */
class AkkaSenderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var actor_path: String = _

    override def initialize(config: JsObject) {
        actor_path = (config \ "actor_path").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val selection = Akka.system.actorSelection(actor_path)
        selection ! data.data
        data
    })
}

/**
 * Zips two traversables and explodes the result into separate datums, overwriting the original traversables
 */
class ZipExplodeProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field1: String = _
    var field2: String = _

    override def initialize(config: JsObject) {
        field1 = (config \ "field_1").as[String]
        field2 = (config \ "field_2").as[String]
    }

    private def toList(any: Any): List[Any] = any match {
        case tra: GenTraversableOnce[_] => tra.toList
        case arr: Array[_]              => arr.toList
        case any: Any                   => List(any)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.flatMap(datum => {
            val zipped = toList(datum(field1)).zip(toList(datum(field2)))
            for ((any1, any2) <- zipped) yield datum + (field1 -> any1) + (field2 -> any2)
        })
    })
}

/**
 * Filters out every datum that does not contain any of the required fields.
 */
class AbsentFieldsFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: Set[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[Set[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.filter(datum => fields.subsetOf(datum.keySet))
    })
}

/**
 * Adds a UUID to the datapacket
 */
class UUIDAdderProcessor(resultName: String) extends BaseProcessor(resultName) {

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> java.util.UUID.randomUUID.toString)
        }
    })
}

/**
 * Collects a number of fields and puts them in a list.
 */
class FieldsToListProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> fields.map(field => {
                datum(field)
            }).toList)
        }
    })
}

/**
 * Turns ugly XML into pretty map :)
 */
class XmlToMapProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var trim: Boolean = _
    var nonEmpty: Boolean = _
    var flattened: Boolean = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        trim = (config \ "trim").asOpt[Boolean].getOrElse(false)
        nonEmpty = (config \ "non_empty").asOpt[Boolean].getOrElse(false)
        flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            if (flattened)
                datum ++ utils.xmlToMap(datum(field).asInstanceOf[scala.xml.Node], trim, nonEmpty)
            else
                datum + (resultName -> utils.xmlToMap(datum(field).asInstanceOf[scala.xml.Node], trim, nonEmpty))
        }
    })
}

/**
 * Filters out empty datums and data packets
 */
class RemoveEmptyPacketProcessor(resultName: String) extends BaseProcessor(resultName) {
    var removeEmptyDatums: Boolean = _

    override def initialize(config: JsObject) {
        removeEmptyDatums = (config \ "remove_empty_datums").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        if (removeEmptyDatums)
            data.filter { datum => datum.nonEmpty }
        else
            data
    }) compose Enumeratee.filter { (data: DataPacket) => data.nonEmpty }
}

/**
 * Gets an element of a list at a specified index
 */
class GetListElementProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var index: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        index = (config \ "index").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(data.data.map { datum =>
            val seq = datum(field).asInstanceOf[Seq[Any]]
            val newIndex = utils.evaluateTuktuString(index, datum).toInt
            if (seq.size > newIndex)
                datum + (resultName -> seq(newIndex))
            else datum
        })
    })
}