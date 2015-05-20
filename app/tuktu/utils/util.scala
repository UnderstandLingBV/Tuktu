package tuktu.utils

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.AnyContent
import play.api.mvc.Request

object util {
    /**
     * Recursively traverses a path of keys until it finds a value (or fails to traverse,
     * in which case a default value is used)
     */
    def fieldParser(input: Map[String, Any], path: List[String], defaultValue: Option[Any]): Any = path match {
        case someKey::List() => input(someKey)
        case someKey::trailPath => {
            // Get the remainder
            if (input.contains(someKey)) {
                // See if we can cast it
                try {
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
        case js::trailPath => {
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
                        case _ => json
                    }
                }
            }
        }
    }
    
    /**
     * Turns a JSON string (with enclosing quotes) into a normal string (with no quotes)
     */
    def JsonStringToNormalString(value: JsString) = {
        // Remove the annoying quotes
       value.toString.drop(1).take(value.toString.size - 2)
    }
    
    /**
     * Converts a flash from an HTTP request into a map of messages
     */
    def flashMessagesToMap(request: Request[AnyContent]) = {
        (
            request.flash.get("error") match {
                case Some(error) => Map(
                        "errors" -> error.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("success") match {
                case Some(success) => Map(
                        "success" -> success.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("info") match {
                case Some(info) => Map(
                        "info" -> info.split(";").toList
                )
                case _ => Map()
            }
        )
    }
}