package tuktu.web.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils
import tuktu.api.WebJsFunctionObject
import tuktu.api.WebJsCodeObject

/**
 * Overwrites certain CSS classes and definitions. Useful if you want to apply a specific
 * styling to a lot of elements with the same class or type at once.
 */
class CssApplierProcessor(resultName: String) extends BaseProcessor(resultName) {
    var cssContent: String = _
    
    override def initialize(config: JsObject) {
        cssContent = (config \ "css").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            val evalContent = utils.evaluateTuktuString(cssContent, datum)
            
            datum + ((resultName + "_fnc") -> new WebJsFunctionObject(
                    "setStyle",
                    List("cssTest"),
                    "var sheet = document.createElement('style');" +
                    "sheet.type = 'text/css';" +
                    "window.customSheet = sheet;" +
                    "(document.head || document.getElementsByTagName('head')[0]).appendChild(sheet);" +
                    "return (setStyle = function(cssText, node) {" +
                        "if(!node || node.parentNode !== sheet)" +
                            "return sheet.appendChild(document.createTextNode(cssText));" +
                        "node.nodeValue = cssText;" +
                        "return node;" +
                    "})(cssText);"
            )) + (resultName -> new WebJsCodeObject(
                    "setStyle(" + evalContent + ")"
            ))
        }
    })
}