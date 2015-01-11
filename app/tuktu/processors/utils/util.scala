package tuktu.processors.utils

import play.api.libs.json.JsValue
import play.api.libs.json.JsString

object util {
    def fieldParser(input: Map[String, Any], path: List[String], defaultValue: Option[Any]): Any = path match {
        case someKey::List() => input(someKey)
        case someKey::trailPath => {
            // Get the remainder
            if (input.contains(someKey)) {
                fieldParser(input(someKey).asInstanceOf[Map[String, Any]], trailPath, defaultValue)
            } else {
                // Couldn't find it
                defaultValue.getOrElse(null)
            }
        }
    }
    
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
    
    def JsonStringToNormalString(value: JsString) = {
        // Remove the annoying quotes
       value.toString.drop(1).take(value.toString.size - 2)
    }
}