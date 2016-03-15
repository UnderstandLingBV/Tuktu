package tuktu.web.js

import tuktu.api.DataPacket
import tuktu.api.WebJsObject
import tuktu.api.WebJsNextFlow
import tuktu.api.BaseJsObject
import tuktu.api.WebJsCodeObject
import tuktu.api.WebJsEventObject
import tuktu.api.utils
import tuktu.api.WebJsFunctionObject
import tuktu.api.WebJsSrcObject
import tuktu.api.WebJsOrderedObject
import play.api.Play

object JSGeneration {
    /**
     * Turns a data packet into javascript that can be executed and returned by Tuktu
     */
    def PacketToJsBuilder(dp: DataPacket): (String, Option[String], List[String]) = {
        var nextFlow: Option[String] = None
        val includes = collection.mutable.ListBuffer.empty[String]
        val jsField = Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field")
        
        val res = (for {
            datum <- dp.data
            
            // Check if our js field is there
            if (datum.contains(jsField) && {
                datum(jsField) match {
                    case a: WebJsOrderedObject => true
                    case _ => false
                }
            })
        } yield {
            // Iterate over the JS elements
            val jsElements = datum(jsField).asInstanceOf[WebJsOrderedObject].items
            
            jsElements.flatMap(jsElement => jsElement.map(elem => elem match {
                case (dKey, dValue) => {
                    // Side effect
                    dValue match {
                        case a: WebJsNextFlow => nextFlow = Some(a.flowName)
                        case a: WebJsSrcObject => includes += a.url
                        case a: Any => {}
                    }
                    
                    handleJsObject(datum, dKey, dValue).trim().replaceAll("\r\n|\r|\n", "")
                }
            }))
        }).flatten.toList.filter(!_.isEmpty).mkString(";")
        
        (res, nextFlow, includes.toList)
    }
    
    /**
     * Turns a single JS object into proper JS code by decorating it
     */
    def handleJsObject(datum: Map[String, Any], key: String, value: Any): String = {
        value match {
            case aVal: WebJsOrderedObject =>
                // Nested items, process them in-order
                aVal.items.map(item => handleJsObject(datum, key, item)).toList.filter(!_.isEmpty).mkString(";")
            case aVal: WebJsObject => {
                // Get the value to obtain and place it in a key with a proper name that we will collect
                "tuktuvars." + key + " = " + (aVal.js match {
                    case v: String if (v.startsWith("function")) => v
                    case v: String if !aVal.noQoutes => "'" + v + "'"
                    case _ => aVal.js.toString
                })
            }
            case aVal: WebJsCodeObject => {
                // Output regular JS code
                aVal.code
            }
            case aVal: WebJsEventObject => {
                // Add event listener
                "$(document).on('" + aVal.event + "','" + aVal.selector + "'," + aVal.callback + ");"
            }
            case aVal: WebJsFunctionObject => {
                // Add function
                "function " + aVal.name + "(" + aVal.functionParams.mkString(",") + ")" +
                "{" + aVal.functionBody + "}"
            }
            case _ => ""
        }
    }
}