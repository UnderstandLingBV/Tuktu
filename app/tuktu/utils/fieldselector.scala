package tuktu.utils

import play.api.libs.json.JsValue

object FieldSelector {
    /**
     * Uses a pre-defined list of names to construct the key with 
     */
    private def SomeFields(fields: List[String]): Map[String, Any] => String = data => {
        fields.foldLeft("")(_ + data(_).toString)
    }
    /**
     * Uses all fields of the map to construct the key
     */
    private def AllFields(): Map[String, Any] => String = data => {
        data.foldLeft("")(_ + _._2.toString)
    }
    
    
    /**
     * Gets a key based on a config
     */
    def KeyFromConfig(config: JsValue) = {
        val selector = (config \ "type").as[String]
        selector match {
            case "all" => AllFields
            case "some" => {
                // Get the fields to obtain
                val fields = (config \ "fields").as[List[String]]
                SomeFields(fields)
            }
        }
    }
}