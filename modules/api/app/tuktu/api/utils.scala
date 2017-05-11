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
        case class TuktuStringRoot(children: Seq[TuktuStringNode]) {
            lazy val containsFunction: Boolean = children.exists { _.isInstanceOf[TuktuStringFunction] }
            lazy val asString: String = children match {
                case Nil                       => ""
                case Seq(TuktuStringString(s)) => s
                // Else throw exception
            }
            def evaluate(vars: Map[String, Any]): String = evaluateTuktuString(this, vars)
            override def toString: String = children.mkString
        }
        case class TuktuStringString(string: String) extends TuktuStringNode {
            override def toString: String = string
        }
        case class TuktuStringFunction(function: Option[String], children: Seq[TuktuStringNode]) extends TuktuStringNode {
            def evaluateKey(vars: Map[String, Any]): String = children.foldLeft("") {
                case (acc, TuktuStringString(s))   => acc + s
                case (acc, a: TuktuStringFunction) => acc + a.evaluate(vars)
            }
            def evaluate(vars: Map[String, Any]): String = {
                // Get the key first
                val key: String = evaluateKey(vars)

                // Try to get value at key; does support dot notation
                val value = fieldParser(vars, key)
                function match {
                    case None => value match {
                        case None               => "${" + key + "}"
                        case Some(js: JsString) => js.value
                        case Some(a)            => a.toString
                    }
                    case Some("JSON.stringify") => AnyToJsValue(value).toString
                    case Some("SQL") =>
                        def evaluateSQLParameter(any: Any): String = any match {
                            case None | null => "NULL"
                            case Some(el)    => evaluateSQLParameter(el)
                            case el: JsValue => evaluateSQLParameter(JsValueToAny(el))
                            case el: Boolean => if (el) "1" else "0"
                            case el: String  => "'" + el.replace("'", "''") + "'"
                            case _           => any.toString
                        }
                        evaluateSQLParameter(value)
                    case Some("SplitGet") => {
                        // This function takes more than just a key
                        val split = key.split(",")
                        if (split.size != 3) "null"
                        else {
                            // Get the real value now
                            val splitChar = split(1)
                            val splitIndex = split(2).toInt
                            val realValue = fieldParser(vars, split(0))
                            realValue match {
                                case None               => "null"
                                case Some(rv: JsString) => rv.value.split(splitChar)(splitIndex)
                                case Some(rv: String)   => rv.split(splitChar)(splitIndex)
                                case Some(rv)           => rv.toString.split(splitChar)(splitIndex)
                            }
                        }
                    }
                    case Some("GetOrNull") => value match {
                        case None               => "null"
                        case Some(js: JsString) => js.value
                        case Some(a)            => a.toString
                    }
                    case Some(function) =>
                        throw new IllegalArgumentException("TuktuString function " + function + " does not exist.")
                }
            }
            override def toString: String = "$" + function.getOrElse("") + "{" + children.mkString + "}"
        }

        // Supported functions; empty String not properly supported by StringIn, so we will use Option instead
        val functionNames: Seq[String] = Seq("JSON.stringify", "SQL", "SplitGet", "GetOrNull")

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

        // Minify Tree by combining neighboring TuktuStringStrings
        def minify(root: TuktuStringRoot): TuktuStringRoot = {
            def helper(rest: Seq[TuktuStringNode]): Seq[TuktuStringNode] = rest match {
                case Nil => Nil
                case TuktuStringString(s1) +: TuktuStringString(s2) +: tail => helper(TuktuStringString(s1 + s2) +: tail)
                case (s: TuktuStringString) +: tail => s +: helper(tail)
                case TuktuStringFunction(f, c) +: tail => TuktuStringFunction(f, helper(c)) +: helper(tail)
            }
            TuktuStringRoot(helper(root.children))
        }
        // Prepare string into a minified parsed tree
        def prepare(str: String): TuktuStringRoot = prepare(str, '$')
        def prepare(str: String, specialChar: Char): TuktuStringRoot =
            minify(
                specialChar match {
                    case '$' => tuktuTotal.parse(str).get.value
                    case '#' => configTotal.parse(str).get.value
                })
        // Evaluate Tuktu String given a map of variables
        def apply(root: TuktuStringRoot, vars: Map[String, Any]): String =
            root.children.foldLeft("") {
                case (acc, TuktuStringString(s))   => acc + s
                case (acc, a: TuktuStringFunction) => acc + a.evaluate(vars)
            }
        def apply(str: String, vars: Map[String, Any], specialChar: Char = '$'): String =
            apply(prepare(str, specialChar), vars)
    }

    /**
     * Recursively evaluates a Tuktu config to resolve variables in it
     */
    trait IsMinified {
        val isMinified: Boolean = true
    }
    trait NotMinified {
        val isMinified: Boolean = false
    }
    abstract class PreparedJsNode {
        def evaluate(vars: Map[String, Any]): JsValue
        def evaluate: JsValue
        val isMinified: Boolean
        def minify: PreparedJsNode
    }

    case class PreparedJsValue(js: JsValue) extends PreparedJsNode with IsMinified {
        def evaluate(vars: Map[String, Any]): JsValue = js
        def evaluate: JsValue = js
        def minify: PreparedJsNode = this
    }
    abstract class PreparedJsObject extends PreparedJsNode {
        def evaluate(vars: Map[String, Any]): JsObject
        def evaluate: JsObject
        def minify: PreparedJsObject
    }
    case class PreparedDefaultJsObject(js: JsObject) extends PreparedJsObject with IsMinified {
        def evaluate(vars: Map[String, Any]): JsObject = js
        def evaluate: JsObject = js
        def minify: PreparedJsObject = this
    }
    case class PreparedExtendedJsObject(seq: Seq[(Either[String, evaluateTuktuString.TuktuStringRoot], PreparedJsNode)]) extends PreparedJsObject with NotMinified {
        def evaluate(vars: Map[String, Any]): JsObject = JsObject(
            seq.map {
                case (Left(s), n)  => s -> n.evaluate(vars)
                case (Right(r), n) => r.evaluate(vars) -> n.evaluate(vars)
            })
        def evaluate: JsObject = throw new NoSuchMethodException("Cannot evaluate extended JsObject that contains Tuktu Strings.")
        def minify: PreparedJsObject = {
            val minifiedValues = seq.map { case (key, value) => key -> value.minify }
            if (minifiedValues.forall { case (key, value) => key.isLeft && value.isMinified })
                PreparedDefaultJsObject(JsObject(minifiedValues.map { case (key, value) => key.left.get -> value.evaluate }))
            else
                PreparedExtendedJsObject(minifiedValues)
        }
    }
    abstract class PreparedJsArray extends PreparedJsNode {
        def evaluate(vars: Map[String, Any]): JsArray
        def evaluate: JsArray
        def minify: PreparedJsArray
    }
    case class PreparedDefaultJsArray(js: JsArray) extends PreparedJsArray with IsMinified {
        def evaluate(vars: Map[String, Any]): JsArray = js
        def evaluate: JsArray = js
        def minify: PreparedJsArray = this
    }
    case class PreparedExtendedJsArray(seq: Seq[PreparedJsNode]) extends PreparedJsArray with NotMinified {
        def evaluate(vars: Map[String, Any]): JsArray =
            JsArray(seq.map { _.evaluate(vars) })
        def evaluate: JsArray = throw new NoSuchMethodException("Cannot evaluate extended JsArray that contains Tuktu strings.")
        def minify: PreparedJsArray = {
            val minifiedValues = seq.map { _.minify }
            if (minifiedValues.forall { _.isMinified })
                PreparedDefaultJsArray(JsArray(minifiedValues.map { _.evaluate }))
            else
                PreparedExtendedJsArray(minifiedValues)
        }
    }
    case class PreparedJsString(str: evaluateTuktuString.TuktuStringRoot, toParse: Boolean) extends PreparedJsNode with NotMinified {
        def evaluate(vars: Map[String, Any]): JsValue = {
            val evaluated: String = str.evaluate(vars)
            if (toParse)
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
        def evaluate: JsValue = throw new NoSuchMethodException("Cannot evaluate extended JsObject.")
        def minify: PreparedJsNode = this
    }

    def prepareTuktuJsValue(json: JsValue, specialChar: Char = '$'): PreparedJsNode = json match {
        case obj: JsObject  => prepareTuktuJsObject(obj, specialChar)
        case arr: JsArray   => prepareTuktuJsArray(arr, specialChar)
        case str: JsString  => prepareTuktuJsString(str, specialChar)
        case value: JsValue => PreparedJsValue(value) // Nothing to do for any other JsTypes
    }
    def evaluateTuktuJsValue(json: JsValue, vars: Map[String, Any], specialChar: Char = '$'): JsValue =
        prepareTuktuJsValue(json, specialChar).evaluate(vars)

    def prepareTuktuJsObject(obj: JsObject, specialChar: Char = '$'): PreparedJsObject = PreparedExtendedJsObject(
        obj.fields.map {
            case (key, value) =>
                val preparedKey = evaluateTuktuString.prepare(key, specialChar)
                val preparedValue = prepareTuktuJsValue(value, specialChar)
                if (preparedKey.containsFunction)
                    Right(preparedKey) -> preparedValue
                else
                    Left(preparedKey.asString) -> preparedValue
        }).minify
    def evaluateTuktuJsObject(obj: JsObject, vars: Map[String, Any], specialChar: Char = '$'): JsObject =
        prepareTuktuJsObject(obj, specialChar).evaluate(vars)

    def prepareTuktuJsArray(arr: JsArray, specialChar: Char = '$'): PreparedJsArray =
        PreparedExtendedJsArray(arr.value.map { value => prepareTuktuJsValue(value, specialChar) }).minify
    def evaluateTuktuJsArray(arr: JsArray, vars: Map[String, Any], specialChar: Char = '$'): JsArray =
        prepareTuktuJsArray(arr, specialChar).evaluate(vars)

    def prepareTuktuJsString(str: JsString, specialChar: Char = '$'): PreparedJsNode = {
        // Check if it has to be parsed
        val toBeParsed = str.value.startsWith(specialChar + "JSON.parse{") && str.value.endsWith("}")
        val cleaned = if (toBeParsed) str.value.drop((specialChar + "JSON.parse{").length).dropRight("}".length) else str.value

        // Evaluate Tuktu Strings in cleaned string
        val evaluated = evaluateTuktuString.prepare(cleaned, specialChar)

        if (toBeParsed)
            if (evaluated.containsFunction)
                PreparedJsString(evaluated, true)
            else
                PreparedJsValue(try
                    Json.parse(evaluated.asString)
                catch {
                    // If we can not parse it, treat it as JsString
                    case e: com.fasterxml.jackson.core.JsonParseException =>
                        play.api.Logger.warn("evaluateTuktuJsString: Couldn't parse to JSON:\n" + evaluated)
                        JsString(evaluated.asString)
                })
        else if (evaluated.containsFunction)
            PreparedJsString(evaluated, false)
        else
            PreparedJsValue(JsString(evaluated.asString))
    }
    def evaluateTuktuJsString(str: JsString, vars: Map[String, Any], specialChar: Char = '$'): JsValue =
        prepareTuktuJsString(str, specialChar).evaluate(vars)

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
        case null                  => JsNull
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
        case JsNull       => null
        case _            => json.toString
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
            Cache.getAs[String]("configRepo").getOrElse(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs")),
            name + { if (name.endsWith(".json")) "" else ".json" })
    }.flatMap(loadConfig)
}
