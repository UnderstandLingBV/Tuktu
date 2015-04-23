package tuktu.api

import java.util.regex.Pattern
import play.api.libs.json._

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
     * Turns a map of string -> any into a JSON object
     */
    def anyMapToJson(map: Map[String, Any]): JsObject = {
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
                case a: JsValue => a
                case a: Seq[Any] => anyListToJsonHelper(a)
                case a: Map[_, _] => mapToJsonHelper(a.toList)
                case _ => head._2.toString
            })) ++ mapToJsonHelper(remainder)
        }
        
        /**
         * Dealing with arrays
         */
        def anyListToJsonHelper(list: Seq[Any]): JsArray = list match {
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
                case a: JsValue => a
                case a: Seq[Any] => anyListToJsonHelper(a)
                case _ => elem.toString
            }) ++ anyListToJsonHelper(remainder)
        }
        
        mapToJsonHelper(map.toList)
    }
}