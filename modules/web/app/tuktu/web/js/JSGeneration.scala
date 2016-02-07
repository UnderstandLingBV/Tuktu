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

object JSGeneration {
    /**
     * Turns a data packet into javascript that can be executed and returned by Tuktu
     */
    def PacketToJsBuilder(dp: DataPacket): (String, Option[String], List[String]) = {
        var nextFlow: Option[String] = None
        val includes = collection.mutable.ListBuffer.empty[String]
        
        val res = (for {
            datum <- dp.data
            (dKey, dValue) <- datum
            
            if (classOf[BaseJsObject].isAssignableFrom(dValue.getClass))
        } yield {
            // Side effect
            dValue match {
                case a: WebJsNextFlow => nextFlow = Some(a.flowName)
                case a: WebJsSrcObject => includes += a.url
                case a: Any => {}
            }
            
            handleJsObject(datum, dKey, dValue).trim().replaceAll("\r\n|\r|\n", "")
        }).toList.filter(!_.isEmpty).mkString(";")
        
        (res, nextFlow, includes.toList)
    }
    
    def handleJsObject(datum: Map[String, Any], key: String, value: Any) = {
        value match {
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
                "var elements=document.querySelectorAll('" + aVal.selector + "');" +
                "for(var i=0,l=elements.length;i<l;i++)" +
                "elements.item(i).addEventListener('" + aVal.event + "'," + aVal.callback + ");"
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