package tuktu.api

import java.nio.file.{ Files, Paths, Path }
import java.util.Date
import scala.util.Try
import scala.util.hashing.MurmurHash3
import org.joda.time.DateTime
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.GenMap
import scala.xml.{ XML, Elem, Node, NodeSeq }
import fastparse.all._

object utils {
    val logDpContent = Cache.getAs[Boolean]("mon.log_dp_content").getOrElse(Play.current.configuration.getBoolean("tuktu.monitor.log_dp_content").getOrElse(true))

    /**
     * Enumeratee for error-logging and handling
     * @param idString A string used to identify the flow this logEnumeratee is part of. A mapping exists
     * within the monitor that maps this ID to a generator name.
     * @param configName The name of the config (if known)
     * @param processorName The name of the processor (if known)
     */
    def logEnumeratee[T](idString: String, configName: String = "Unknown", processorName: String = "Unknown") = Enumeratee.recover[T] {
        case (e, input) => {
            // Notify the monitor so it can kill our flow
            Akka.system.actorSelection("user/TuktuMonitor") ! new ErrorNotificationPacket(idString, configName, processorName, input.toString, e)

            // Log the error
            if (logDpContent)
                play.api.Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString, on Input: " + input, e)
            else
                play.api.Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString", e)
        }
    }

    /**
     * Evaluates a Tuktu string to resolve functions and variables in the actual string
     */
    object evaluateTuktuString {
        // Tree structure
        abstract class TuktuStringNode
        case class TuktuStringRoot(children: Seq[TuktuStringNode])
        case class TuktuStringFunction(function: Option[String], children: Seq[TuktuStringNode]) extends TuktuStringNode
        case class TuktuStringString(string: String) extends TuktuStringNode

        // Supported functions; empty String not properly supported by StringIn, so we will use Option instead
        val functionNames: Seq[String] = Seq("JSON.stringify", "SQL")

        // AnyChar and AnyChar but closing curly bracket
        val anyChar: P[TuktuStringString] = P(AnyChar).!.map { TuktuStringString(_) }
        val string: P[TuktuStringString] = P(CharPred(_ != '}')).!.map { TuktuStringString(_) }

        // Tuktu strings
        val tuktuKey: P[Seq[TuktuStringNode]] = P(tuktuString | string).rep
        val tuktuString: P[TuktuStringFunction] = P("$" ~ StringIn(functionNames: _*).!.? ~ "{" ~ tuktuKey ~ "}").map {
            case (optFunction, key) => TuktuStringFunction(optFunction, key)
        }
        val tuktuTotal: P[TuktuStringRoot] = P(Start ~ (tuktuString | anyChar).rep ~ End).map { TuktuStringRoot(_) }

        // Config strings
        val configKey: P[Seq[TuktuStringNode]] = P(configString | string).rep
        val configString: P[TuktuStringFunction] = P("#" ~ StringIn(functionNames: _*).!.? ~ "{" ~ configKey ~ "}").map {
            case (optFunction, key) => TuktuStringFunction(optFunction, key)
        }
        val configTotal: P[TuktuStringRoot] = P(Start ~ (configString | anyChar).rep ~ End).map { TuktuStringRoot(_) }

        // Evaluate
        def apply(str: String, vars: Map[String, Any], specialChar: Char = '$'): String = {
            def evalFunc(f: TuktuStringFunction): String = {
                // Get the key first
                val key = f.children.foldLeft("") {
                    case (acc, TuktuStringString(s))   => acc + s
                    case (acc, a: TuktuStringFunction) => acc + evalFunc(a)
                }

                // Try to get value at key; does support dot notation
                fieldParser(vars, key) match {
                    // No value found at key, return whole Tuktu String unchanged 
                    case None => specialChar + f.function.getOrElse("") + "{" + key + "}"
                    case Some(a) => f.function match {
                        // We found a value, decide what to do with it based on function, None is empty string, ie. ${key}
                        case None => a match {
                            case js: JsString => js.value
                            case _            => a.toString
                        }
                        case Some("JSON.stringify") => AnyToJsValue(a).toString
                        case Some("SQL") =>
                            def evaluateSQLParameter(any: Any): String = any match {
                                case el: JsValue => evaluateSQLParameter(JsValueToAny(el))
                                case el: Boolean => if (el) "1" else "0"
                                case el: String  => "'" + el.replace("'", "''") + "'"
                                case _           => any.toString
                            }
                            evaluateSQLParameter(a)
                        case Some(function) =>
                            play.api.Logger.warn("TuktuString function " + function + " not yet implemented.")
                            specialChar + function + "{" + key + "}"
                    }
                }
            }
            val root = specialChar match {
                case '$' => tuktuTotal.parse(str).get.value
                case '#' => configTotal.parse(str).get.value
            }
            root.children.foldLeft("") {
                case (acc, TuktuStringString(s))   => acc + s
                case (acc, a: TuktuStringFunction) => acc + evalFunc(a)
            }
        }
    }

    /**
     * Recursively evaluates a Tuktu config to resolve variables in it
     */
    def evaluateTuktuJsValue(json: JsValue, vars: Map[String, Any], specialChar: Char = '$'): JsValue = json match {
        case obj: JsObject  => evaluateTuktuJsObject(obj, vars, specialChar)
        case arr: JsArray   => evaluateTuktuJsArray(arr, vars, specialChar)
        case str: JsString  => evaluateTuktuJsString(str, vars, specialChar)
        case value: JsValue => value // Nothing to do for any other JsTypes
    }

    def evaluateTuktuJsObject(obj: JsObject, vars: Map[String, Any], specialChar: Char = '$'): JsObject = {
        new JsObject(obj.value.map { case (key, value) => evaluateTuktuString(key, vars, specialChar) -> evaluateTuktuJsValue(value, vars, specialChar) }.toSeq)
    }

    def evaluateTuktuJsArray(arr: JsArray, vars: Map[String, Any], specialChar: Char = '$'): JsArray = {
        new JsArray(arr.value.map(value => evaluateTuktuJsValue(value, vars, specialChar)))
    }

    def evaluateTuktuJsString(str: JsString, vars: Map[String, Any], specialChar: Char = '$'): JsValue = {
        // Check if it has to be parsed
        val toBeParsed = str.value.startsWith(specialChar + "JSON.parse{") && str.value.endsWith("}")
        val cleaned = if (toBeParsed) str.value.drop((specialChar + "JSON.parse{").length).dropRight("}".length) else str.value

        // Evaluate Tuktu Strings in cleaned string
        val evaluated = evaluateTuktuString(cleaned, vars, specialChar)

        if (toBeParsed)
            try
                Json.parse(evaluated)
            catch {
                // If we can not parse it, treat it as JsString
                case e: com.fasterxml.jackson.core.JsonParseException =>
                    play.api.Logger.warn("evaluateTuktuJsString: Couldn't parse to JSON:\n" + evaluated)
                    JsString(evaluated)
            }
        else
            JsString(evaluated)
    }

    /**
     * Recursively traverses a path of keys until it finds its value
     * (or fails to traverse, in which case None is returned)
     */
    def fieldParser(input: Map[String, Any], path: String): Option[Any] = {
        input.get(path) match {
            case Some(a) => Some(a)
            case None =>
                if (path.isEmpty)
                    Some(input)
                else
                    fieldParser(input, path.split('.').toList)
        }
    }
    def fieldParser(input: Map[String, Any], path: List[String]): Option[Any] = path match {
        case Nil            => Some(input)
        case someKey :: Nil => input.get(someKey)
        case someKey :: trailPath => input.get(someKey).flatMap {
            // Handle remainder depending on type
            case js: JsValue           => jsonParser(js, trailPath)
            case map: Map[String, Any] => fieldParser(map, trailPath)
            case _                     => None
        }
    }

    /**
     * Recursively traverses a JSON object of keys until it finds a value
     * (or fails to traverse, in which case None is returned)
     */
    def jsonParser(json: JsValue, jsPath: List[String]): Option[JsValue] = jsPath match {
        case Nil             => Some(json)
        case js :: trailPath => (json \ js).asOpt[JsValue].flatMap { _json => jsonParser(_json, trailPath) }
    }

    /**
     * ---------------------
     * JSON helper functions
     * ---------------------
     */

    /**
     * Turns Any into a JsValueWrapper to use by Json.arr and Json.obj
     */
    private def AnyToJsValueWrapper(a: Any, mongo: Boolean = false): Json.JsValueWrapper = a match {
        case a: Boolean            => a
        case a: String             => a
        case a: Char               => a.toString
        case a: Short              => a
        case a: Int                => a
        case a: Long               => a
        case a: Float              => if (a.isNaN || a.isInfinite || a.isPosInfinity || a.isNegInfinity) a.toString else a
        case a: Double             => if (a.isNaN || a.isInfinite || a.isPosInfinity || a.isNegInfinity) a.toString else a
        case a: BigDecimal         => a
        case a: Date               => if (!mongo) a else Json.obj("$date" -> a.getTime)
        case a: DateTime           => if (!mongo) a else Json.obj("$date" -> a.getMillis)
        case a: JsValue            => a
        case None                  => JsNull
        case Some(b)               => AnyToJsValueWrapper(b, mongo)
        case a: (_, _)             => MapToJsObject(Map(a), mongo)
        case a: GenMap[_, _]       => MapToJsObject(a, mongo)
        case a: TraversableOnce[_] => SeqToJsArray(a.toSeq, mongo)
        case a: Array[_]           => SeqToJsArray(a, mongo)
        case null                  => "null" // Why not JsNull?
        case _                     => a.toString
    }

    /**
     * Turns Any into a JsValue
     */
    def AnyToJsValue(a: Any, mongo: Boolean = false): JsValue =
        Json.arr(AnyToJsValueWrapper(a, mongo))(0)

    /**
     * Turns any Seq[Any] into a JsArray
     */
    def SeqToJsArray(seq: Seq[_], mongo: Boolean = false): JsArray =
        Json.arr(seq.map(value => AnyToJsValueWrapper(value, mongo)): _*)

    /**
     * Turns a Map[Any, Any] into a JsObject
     */
    def MapToJsObject(map: GenMap[_, _], mongo: Boolean = false): JsObject =
        Json.obj(map.map { case (key, value) => key.toString -> AnyToJsValueWrapper(value, mongo) }.toList: _*)

    /**
     * Takes a JsValue and returns a scala object
     */
    def JsValueToAny(json: JsValue): Any = json match {
        case a: JsString  => a.value
        case a: JsNumber  => a.value
        case a: JsBoolean => a.value
        case a: JsObject  => JsObjectToMap(a)
        case a: JsArray   => JsArrayToSeqAny(a)
        case a            => a.toString
    }

    /**
     * Converts a JsArray to a Seq[Any]
     */
    def JsArrayToSeqAny(arr: JsArray): Seq[Any] =
        for (field <- arr.value) yield JsValueToAny(field)

    /**
     * Converts a JsObject to Map[String, Any]
     */
    def JsObjectToMap(json: JsObject): Map[String, Any] =
        json.fields.map { case (key, value) => key -> JsValueToAny(value) }.toMap

    /**
     * -----------------------------
     * Node hashing helper functions
     * -----------------------------
     */

    def indexToNodeHasher(keys: List[Any], replicationCount: Option[Int], includeSelf: Boolean): List[String] =
        indexToNodeHasher(keys.map(_.toString).mkString(""), replicationCount, includeSelf)
    /**
     * Hashes an index to a (number of) node(s)
     */
    def indexToNodeHasher(keyString: String, replicationCount: Option[Int], includeSelf: Boolean): List[String] = {
        def indexToNodeHasherHelper(nodes: List[String], replCount: Int): List[String] = {
            // Get a node
            val node = nodes(Math.abs(MurmurHash3.stringHash(keyString) % nodes.size))

            // Return more if required
            node :: {
                if (replCount > 1) {
                    indexToNodeHasherHelper(nodes diff List(node), replCount - 1)
                } else List()
            }
        }

        val clusterNodes = {
            if (includeSelf)
                Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()).keys.toList
            else
                Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map()).keys.toList diff
                    List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
        }
        replicationCount match {
            case Some(cnt) => indexToNodeHasherHelper(clusterNodes, cnt - {
                if (includeSelf) 1 else 0
            })
            case None => clusterNodes
        }
    }

    /**
     * Gets data from an XML document based on a query that iteratively selects fields or attributes
     */
    def xmlQueryParser(xml: Node, query: List[JsObject]): List[Node] = query match {
        case Nil => List(xml)
        case selector :: trail => {
            // Get the element based on the type of selector
            val selectType = (selector \ "type").as[String]
            val selectString = (selector \ "string").as[String]
            selectType match {
                case "\\" => (xml \ selectString).asInstanceOf[NodeSeq].flatMap(xmlQueryParser(_, trail)).toList
                case _    => (xml \\ selectString).asInstanceOf[NodeSeq].flatMap(xmlQueryParser(_, trail)).toList
            }
        }
    }

    /**
     * Converts an XML document into a map
     */
    def xmlToMap(xml: Node, trim: Boolean = true, nonEmpty: Boolean = true): Map[String, Any] = {
        // Parse the node itself
        val label = xml.label
        val attributes = xml.attributes.asAttrMap
        val text = if (trim) xml.text.trim else xml.text

        // Parse children
        val children = xml.child.filter(el => if (nonEmpty) !el.text.trim.isEmpty else true).map(child => {
            // Recurse
            xmlToMap(child, trim, nonEmpty)
        })

        // Return result
        Map(label -> Map(
            "attributes" -> attributes,
            "text" -> text,
            "children" -> children.toList))
    }

    /**
     * Recursive function to merge two JSON objects
     */
    def mergeJson(a: JsObject, b: JsObject): JsObject = {
        def merge(existingObject: JsObject, otherObject: JsObject): JsObject = {
            val result = existingObject.value ++ otherObject.value.map {
                case (otherKey, otherValue) =>
                    val maybeExistingValue = existingObject.value.get(otherKey)

                    val newValue = (maybeExistingValue, otherValue) match {
                        case (Some(e: JsObject), o: JsObject) => merge(e, o)
                        case _                                => otherValue
                    }
                    otherKey -> newValue
            }
            JsObject(result.toSeq)
        }
        merge(a, b)
    }

    /**
     * Merges maps and JSON intertwined
     */
    def mergeMap(a: Map[String, Any], b: Map[String, Any]) = {
        def merge(existingObject: Map[String, Any], otherObject: Map[String, Any]): Map[String, Any] = {
            existingObject ++ otherObject.map {
                case (otherKey, otherValue) =>
                    val maybeExistingValue = existingObject.get(otherKey)

                    val newValue = (maybeExistingValue, otherValue) match {
                        case (Some(e: JsObject), o: JsObject) => mergeJson(e, o)
                        case (Some(e: Map[String, Any]), o: Map[String, Any]) => merge(e, o)
                        case _ => otherValue
                    }
                    otherKey -> newValue
            }
        }
        merge(a, b)
    }

    /**
     * Merges two Tuktu configs, generators by index and processors by id
     */
    def mergeConfig(oldConfig: JsObject, overwrite: JsObject): JsObject = {
        // Merge generators by index
        val oldGens = (oldConfig \ "generators").asOpt[List[JsObject]].getOrElse(Nil)
        val newGens = (overwrite \ "generators").asOpt[List[JsObject]].getOrElse(Nil)
        val mergedGenerators = oldGens.zipAll(newGens, new JsObject(Nil), new JsObject(Nil)).map { case (c1, c2) => mergeJson(c1, c2) }

        // Merge processors by id
        val oldProcs = (oldConfig \ "processors").asOpt[List[JsObject]].getOrElse(Nil).groupBy(_ \ "id").mapValues(_.head)
        val newProcs = (overwrite \ "processors").asOpt[List[JsObject]].getOrElse(Nil).groupBy(_ \ "id").mapValues(_.head)
        val mergedProcessors = for (id <- oldProcs.keySet ++ newProcs.keySet) yield mergeJson(oldProcs.getOrElse(id, new JsObject(Nil)), newProcs.getOrElse(id, new JsObject(Nil)))

        // Build merged config
        Json.obj("generators" -> mergedGenerators, "processors" -> mergedProcessors)
    }

    /**
     * Checks if two Doubles are nearly equal
     * Source: http://floating-point-gui.de/errors/comparison/
     */
    def nearlyEqual(a: Double, b: Double, epsilon: Double = 0.000000001): Boolean = {
        val absA = math.abs(a)
        val absB = math.abs(b)
        val diff = math.abs(a - b)

        if (a == b)
            // Shortcut, if they happen to actually coincide, also handles infinity
            true
        else if (a == 0 || b == 0 || diff < java.lang.Double.MIN_NORMAL)
            // Use absolute error if one is zero or they are both extremely close together
            diff < epsilon * java.lang.Double.MIN_NORMAL
        else
            // Use relative error
            diff / math.min(absA + absB, Double.MaxValue) < epsilon
    }

    /**
     * Loads a config from the config folder
     */
    def loadConfig(path: Path): Try[JsObject] = Try {
        Json.parse(Files.readAllBytes(path)).as[JsObject]
    }
    def loadConfig(name: String): Try[JsObject] = Try {
        Paths.get(
            Cache.getAs[String]("configRepo").getOrElse("configs"),
            name + { if (name.endsWith(".json")) "" else ".json" })
    }.flatMap(loadConfig)
}
