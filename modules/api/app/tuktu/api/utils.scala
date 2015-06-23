package tuktu.api

import java.util.regex.Pattern
import play.api.libs.json._
import java.util.Date
import org.joda.time.DateTime

object utils {
    val pattern = Pattern.compile("\\$\\{(.*?)\\}")

    /**
     * Evaluates a Tuktu string to resolve variables in the actual string
     */
    def evaluateTuktuString(str: String, vars: Map[String, Any]) = {
        // Set up matcher and string buffer
        val matcher = pattern.matcher(str)
        val buff = new StringBuffer(str.length)

        // Replace with vars
        while (matcher.find)
            matcher.appendReplacement(buff, vars(matcher.group(1)).toString)
        matcher.appendTail(buff)

        // Return buffer
        buff.toString
    }

    /**
     * Checks if a string contains variables that can be populated using evaluateTuktuString
     */
    def containsTuktuStringVariable(str: String) = {
        val matcher = pattern.matcher(str)
        matcher.find()
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
     * Turns a map of string -> any into a JSON object
     */
    def anyMapToJson(map: Map[String, Any], mongo: Boolean = false): JsObject = {
        /**
         * Dealing with objects
         */
        def mapToJsonHelper(mapping: List[(Any, Any)]): JsObject = mapping match {
            case Nil => Json.obj()
            case head :: remainder => Json.obj(head._1.toString -> (head._2 match {
                case a: String     => a
                case a: Char       => a.toString
                case a: Int        => a
                case a: Double     => a
                case a: Long       => a
                case a: Short      => a
                case a: Float      => a
                case a: Boolean    => a
                case a: Date       => if (mongo) Json.obj("$date" -> a.getTime) else a
                case a: DateTime   => if (mongo) Json.obj("$date" -> a.getMillis) else a
                case a: JsValue    => a
                case a: BigDecimal => a
                case a: Seq[Any]   => anyListToJsonHelper(a)
                case a: Map[_, _]  => mapToJsonHelper(a.toList)
                case _             => head._2.toString
            })) ++ mapToJsonHelper(remainder)
        }

        /**
         * Dealing with arrays
         */
        def anyListToJsonHelper(list: Seq[Any], mongo: Boolean = false): JsArray = list.toList match {
            case Nil => Json.arr()
            case elem :: remainder => Json.arr(elem match {
                case a: String     => a
                case a: Char       => a.toString
                case a: Int        => a
                case a: Double     => a
                case a: Long       => a
                case a: Short      => a
                case a: Float      => a
                case a: Boolean    => a
                case a: Date       => if (mongo) Json.obj("$date" -> a.getTime) else a
                case a: DateTime   => if (mongo) Json.obj("$date" -> a.getMillis) else a
                case a: JsValue    => a
                case a: BigDecimal => a
                case a: Seq[Any]   => anyListToJsonHelper(a)
                case a: Map[_, _]  => mapToJsonHelper(a.toList)
                case _             => elem.toString
            }) ++ anyListToJsonHelper(remainder)
        }

        mapToJsonHelper(map.toList)
    }

    /**
     * ---------------------
     * JSON helper functions
     * ---------------------
     */

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
}