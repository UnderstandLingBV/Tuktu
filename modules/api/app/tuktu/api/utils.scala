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
                Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString, on Input: " + input, e)
            else
                Logger.error(s"Error happened at flow: $configName, processor: $processorName, id: $idString", e)
        }
    }

    /**
     * Evaluates a Tuktu string to resolve variables in the actual string
     */
    def evaluateTuktuString(str: String, vars: Map[String, Any], specialChar: Char = '$'): String = {
        // Supported functions; empty String not properly supported by StringIn, so we will use Option instead
        val functionNames: Seq[String] = Seq("JSON.stringify")

        // A key can contain more Tuktu Strings, or go until closing } bracket
        def key: P[String] = P(tuktuString | CharPred(_ != '}').!).rep.map { _.mkString }
        // A Tuktu String is of the form $function{key}, try to get value at key, and do something with its value based on function
        def tuktuString: P[String] = P(specialChar.toString ~ StringIn(functionNames: _*).!.? ~ "{" ~ key ~ "}").map {
            case (optFunction, key) =>
                // Try to get value at key; does support dot notation
                fieldParser(vars, key) match {
                    // No value found at key, return whole Tuktu String unchanged 
                    case None => specialChar + optFunction.getOrElse("") + "{" + key + "}"
                    case Some(a) => optFunction match {
                        // We found a value, decide what to do with it based on function, None is empty string, ie. ${key}
                        case None => a match {
                            case js: JsString => js.value
                            case _            => a.toString
                        }
                        case Some("JSON.stringify") => AnyToJsValue(a).toString
                    }
                }
        }
        // Repeatedly parse either a tuktuString or anything (tuktuString first, hence higher priority, with full backtrack)
        val either: P[String] = P(tuktuString | AnyChar.!)
        val total: P[String] = P(Start ~ either.rep ~ End).map { _.mkString }

        // Parse str and return its value
        total.parse(str).get.value
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
        case a: Float              => a
        case a: Double             => a
        case a: BigDecimal         => a
        case a: Date               => if (!mongo) a else Json.obj("$date" -> a.getTime)
        case a: DateTime           => if (!mongo) a else Json.obj("$date" -> a.getMillis)
        case a: JsValue            => a
        case a: (_, _)             => MapToJsObject(Map(a._1 -> a._2), mongo)
        case a: Map[_, _]          => MapToJsObject(a, mongo)
        case a: TraversableOnce[_] => SeqToJsArray(a.toSeq, mongo)
        case a: Array[_]           => SeqToJsArray(a, mongo)
        case _                     => if (a == null) "null" else a.toString
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
}
