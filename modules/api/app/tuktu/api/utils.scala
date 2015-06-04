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
     * Turns a map of string -> any into a JSON object
     */
    def anyMapToJson(map: Map[String, Any], mongo: Boolean = false): JsObject = {
        /**
         * Dealing with objects
         */
        def mapToJsonHelper(mapping: List[(Any, Any)]): JsObject = mapping match {
            case Nil => Json.obj()
            case head::remainder => Json.obj(head._1.toString -> (head._2 match {
                case a: String => a
                case a: Char => a.toString
                case a: Int => a
                case a: Double => a
                case a: Long => a
                case a: Short => a
                case a: Float => a
                case a: Boolean => a
                case a: Date => if(mongo) Json.obj("$date" -> a.getTime) else a
                case a: DateTime => if(mongo) Json.obj("$date" -> a.getMillis) else a
                case a: JsValue => a
                case a: Seq[Any] => anyListToJsonHelper(a)
                case a: Map[_, _] => mapToJsonHelper(a.toList)
                case _ => head._2.toString
            })) ++ mapToJsonHelper(remainder)
        }
        
        /**
         * Dealing with arrays
         */
        def anyListToJsonHelper(list: Seq[Any], mongo: Boolean = false): JsArray = list match {
            case Nil => Json.arr()
            case elem::remainder => Json.arr(elem match {
                case a: String => a
                case a: Char => a.toString
                case a: Int => a
                case a: Double => a
                case a: Long => a
                case a: Short => a
                case a: Float => a
                case a: Boolean => a
                case a: Date => if(mongo) Json.obj("$date" -> a.getTime) else a
                case a: DateTime => if(mongo) Json.obj("$date" -> a.getMillis) else a
                case a: JsValue => a
                case a: Seq[Any] => anyListToJsonHelper(a)
                case a: Map[_, _] => mapToJsonHelper(a.toList)
                case _ => elem.toString
            }) ++ anyListToJsonHelper(remainder)
        }
        
        mapToJsonHelper(map.toList)
    }
    
    /**
     * ---------------------
     * JSON helper functions
     * ---------------------
     */
    
    def sequenceToMap(fields: Seq[(String, JsValue)]): Map[String, Any] =
        (for (field <- fields) yield JsValueToAny(field)).toMap

    def JsValueToAny(field: (String, JsValue)): (String, Any) =
        field._1 -> (field._2 match {
            case a: JsString  => a.value
            case a: JsNumber  => a.value
            case a: JsBoolean => a.value
            case a: JsObject  => sequenceToMap(a.fields)
            case a: JsArray   => arrayToAny(a)
            case a            => a.toString
        })

    def arrayToAny(arr: JsArray): Any =
        for (field <- arr.value) yield field match {
            case a: JsString  => a.value
            case a: JsNumber  => a.value
            case a: JsBoolean => a.value
            case a: JsObject  => sequenceToMap(a.fields)
            case a: JsArray   => arrayToAny(a)
            case a            => a.toString
        }
    
    /**
     * Takes a JsValue and returns a scala object
     */
    def anyJsonToAny(json: JsValue): Any = json match {
        case a: JsString  => a.value
        case a: JsNumber  => a.value
        case a: JsBoolean => a.value
        case a: JsObject  => sequenceToMap(a.fields)
        case a: JsArray   => arrayToAny(a)
        case a            => a.toString
    }

    /**
     * Convert A Json to Map[String, Any]
     * 
     */
    def anyJsonToMap(json: JsObject): Map[String, Any] = sequenceToMap(json.fields)
}