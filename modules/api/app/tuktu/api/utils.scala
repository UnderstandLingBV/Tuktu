package tuktu.api

import java.util.Date
import java.util.regex.Pattern
import scala.util.hashing.MurmurHash3
import org.joda.time.DateTime
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.concurrent.Akka
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play
import scala.xml.XML
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.collection.mutable.ArrayBuffer


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
                Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString, on Input: " + input, e)
            else
                Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString", e)
        }
    }

    /**
     * Evaluates a Tuktu string to resolve variables in the actual string
     */
    def evaluateTuktuString(str: String, vars: Map[String, Any]) = {
        if (vars.isEmpty) {
            str
        } else {
            // determine max length for performance reasons, include the ,true option
            val maxKeyLength = vars.maxBy(kv => kv._1.length)._1.length + ",true".size
            val result = new StringBuilder
            // a temporary buffer to determine if we need to replace this
            val buffer = new StringBuilder
            // The prefix length of TuktuStrings "${".length = 2
            val prefixSize = "${".length
            var skipQuote = false
            str.foreach { currentChar =>
                if (skipQuote) {
                    // Skip the closing quote of the string encapsulating a non-string variable
                    skipQuote = false
                }
                else {
                    if (buffer.length == 0) {
                        if (currentChar.equals('$')) {
                            buffer.append(currentChar)
                        } else {
                            result.append(currentChar)
                        }
                    } else if (buffer.length == 1) {
                        buffer.append(currentChar)
                        if (!currentChar.equals('{')) {
                            result.append(buffer)
                            buffer.clear
                        }
                    } else if (buffer.length > maxKeyLength + prefixSize) {
                        result.append(buffer).append(currentChar)
                        buffer.clear
                    } else {
                        if (currentChar.equals('}')) {
                            // Check if we need to modify the buffer in case of non-string values, to remove the quotes
                            val (varName, removeQuotesForNonString) = if (buffer.substring(prefixSize).split(",").size == 2) {
                                val split = buffer.substring(prefixSize).split(",")
                                (split(0), split(1).toBoolean)
                            } else (buffer.substring(prefixSize), false)
                            
                            // Apply with variable in vars, or leave it be if it cannot be found
                            val value = if (vars.contains(varName)) {
                                (vars(varName) match {
                                    // Skip the quotes or not?
                                    case v: Boolean => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: JsBoolean => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: Double => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: Int => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: JsNumber => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: JsString => {
                                        if (removeQuotesForNonString) {
                                            result.setLength(result.length - 1)
                                            skipQuote = true
                                        }
                                        v
                                    }
                                    case v: Any => v.toString
                                }).toString
                            } else buffer + "}"
                            result.append(value)
                            buffer.clear
                        } else {
                            buffer.append(currentChar)
                        }
                    }
                }
            }
            // add any left overs
            result.append(buffer)
            result.toString
        }
    }

    /**
     * Evaluates a Tuktu config to resolve variables in it
     */
    def evaluateTuktuConfig(str: String, vars: Map[String, Any]): JsValue = {
        // Check if str is of the form %{...} and hence is JSON that needs to be parsed
        val toBeParsed = str.startsWith("%{") && str.endsWith("}")
        val cleaned = if (toBeParsed) str.drop(2).dropRight(1) else str

        // Replace all other Tuktu config strings
        val replaced = if (vars.isEmpty) {
            cleaned
        } else {
            // determine max length for performance reasons
            val maxKeyLength = vars.maxBy(kv => kv._1.length)._1.length
            val result = new StringBuilder
            // a temporary buffer to determine if we need to replace a Tuktu config string
            val buffer = new StringBuilder

            // The prefix length of TuktuStrings "#{".length = 2
            val prefixSize = "#{".length
            cleaned.foreach { currentChar =>
                if (buffer.length == 0) {
                    if (currentChar.equals('#')) {
                        buffer.append(currentChar)
                    } else {
                        result.append(currentChar)
                    }
                } else if (buffer.length == 1) {
                    buffer.append(currentChar)
                    if (!currentChar.equals('{')) {
                        result.append(buffer)
                        buffer.clear
                    }
                } else if (buffer.length > maxKeyLength + prefixSize) {
                    result.append(buffer).append(currentChar)
                    buffer.clear
                } else {
                    if (currentChar.equals('}')) {
                        // apply with variable in vars, or leave it be if it cannot be found
                        result.append(vars.getOrElse(buffer.substring(prefixSize), buffer + "}").toString)
                        buffer.clear
                    } else {
                        buffer.append(currentChar)
                    }
                }
            }
            // add any left overs
            result.append(buffer)
            result.toString
        }

        if (toBeParsed)
            try
                Json.parse(replaced)
            catch {
                case e: com.fasterxml.jackson.core.JsonParseException => new JsString("%{" + replaced + "}")
            }
        else
            new JsString(replaced)
    }

    /**
     * Recursively evaluates a Tuktu config to resolve variables in it
     * Overloaded to get the correct return type for every possible use case
     */
    def evaluateTuktuConfig(json: JsValue, vars: Map[String, Any]): JsValue = json match {
        case obj: JsObject  => evaluateTuktuConfig(obj, vars)
        case arr: JsArray   => evaluateTuktuConfig(arr, vars)
        case str: JsString  => evaluateTuktuConfig(str, vars)
        case value: JsValue => value // Nothing to do for any other JsTypes
    }

    def evaluateTuktuConfig(obj: JsObject, vars: Map[String, Any]): JsObject = {
        new JsObject(obj.value.mapValues(value => evaluateTuktuConfig(value, vars)).toSeq)
    }

    def evaluateTuktuConfig(arr: JsArray, vars: Map[String, Any]): JsArray = {
        new JsArray(arr.value.map(value => evaluateTuktuConfig(value, vars)))
    }

    def evaluateTuktuConfig(str: JsString, vars: Map[String, Any]): JsValue = {
        evaluateTuktuConfig(str.value, vars)
    }

    /**
     * Recursively traverses a path of keys until it finds a value (or fails to traverse,
     * in which case a default value is used)
     */
    def fieldParser(input: Map[String, Any], path: List[String], defaultValue: Option[JsValue]): Any = path match {
        case Nil => input
        case someKey :: Nil => {
            if (input.contains(someKey))
                input(someKey)
            else
                defaultValue.getOrElse(null)
        }
        case someKey :: trailPath => {
            // Get the remainder
            if (input.contains(someKey)) {
                // See if we can cast it
                try {
                    if (input(someKey).isInstanceOf[JsValue])
                        jsonParser(input(someKey).asInstanceOf[JsValue], trailPath, defaultValue)
                    else
                        fieldParser(input(someKey).asInstanceOf[Map[String, Any]], trailPath, defaultValue)
                } catch {
                    case e: ClassCastException => defaultValue.getOrElse(null)
                }
            } else {
                // Couldn't find it
                defaultValue.getOrElse(null)
            }
        }
    }

    /**
     * Recursively traverses a JSON object of keys until it finds a value (or fails to traverse,
     * in which case a default value is used)
     */
    def jsonParser(json: JsValue, jsPath: List[String], defaultValue: Option[JsValue]): JsValue = jsPath match {
        case List() => json
        case js :: trailPath => {
            // Get the remaining value from the json
            val newJson = (json \ js).asOpt[JsValue]
            newJson match {
                case Some(nj) => {
                    // Recurse into new JSON
                    jsonParser(nj, trailPath, defaultValue)
                }
                case None => {
                    // Couldn't find it, return the best we can
                    defaultValue match {
                        case Some(value) => value
                        case None        => json
                    }
                }
            }
        }
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
        case a: Boolean    => a
        case a: String     => a
        case a: Char       => a.toString
        case a: Short      => a
        case a: Int        => a
        case a: Long       => a
        case a: Float      => a
        case a: Double     => a
        case a: BigDecimal => a
        case a: Date       => if (!mongo) a else Json.obj("$date" -> a.getTime)
        case a: DateTime   => if (!mongo) a else Json.obj("$date" -> a.getMillis)
        case a: JsValue    => a
        case a: (Any, Any) => MapToJsObject(Map(a._1 -> a._2), mongo)
        case a: Seq[_]     => SeqToJsArray(a, mongo)
        case a: Array[_]   => SeqToJsArray(a, mongo)
        case a: Map[_, _]  => MapToJsObject(a, mongo)
        case a: Iterable[_] => SeqToJsArray(a.toSeq, mongo)
        case _             => if (a == null) "null" else a.toString
    }

    /**
     * Turns Any into a JsValue
     */
    def AnyToJsValue(a: Any, mongo: Boolean = false): JsValue =
        Json.arr(AnyToJsValueWrapper(a, mongo))(0)

    /**
     * Turns any Seq[Any] into a JsArray
     */
    def SeqToJsArray(seq: Seq[Any], mongo: Boolean = false): JsArray =
        Json.arr(seq.map(value => AnyToJsValueWrapper(value, mongo)): _*)

    /**
     * Turns a Map[Any, Any] into a JsObject
     */
    def MapToJsObject(map: Map[_ <: Any, Any], mongo: Boolean = false): JsObject =
        Json.obj(map.map(tuple => tuple._1.toString -> AnyToJsValueWrapper(tuple._2, mongo)).toSeq: _*)

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
        json.value.mapValues(jsValue => JsValueToAny(jsValue)).toMap

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
        case selector::trail => {
            // Get the element based on the type of selector
            val selectType = (selector \ "type").as[String]
            val selectString = (selector \ "string").as[String]
            selectType match {
                case "\\" => (xml \ selectString).asInstanceOf[NodeSeq].flatMap(xmlQueryParser(_, trail)).toList
                case _ => (xml \\ selectString).asInstanceOf[NodeSeq].flatMap(xmlQueryParser(_, trail)).toList
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
                "children" -> children.toList
        ))
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
                        case _                                => otherValue
                    }
                    otherKey -> newValue
            }
        }
        merge(a, b)
    }
}