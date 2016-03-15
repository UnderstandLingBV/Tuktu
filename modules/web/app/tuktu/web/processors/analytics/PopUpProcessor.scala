package tuktu.web.processors.analytics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.WebJsCodeObject
import tuktu.api.utils
import tuktu.api.BaseJsProcessor

/**
 * Shows a pop-up window on the page. Useful for showing (debugging) information to the user.
 */
class PopUpProcessor(resultName: String) extends BaseJsProcessor(resultName) {
    var content: String = _
    var width: Int = _
    var height: Int = _
    
    override def initialize(config: JsObject) {
        content = (config \ "content").as[String]
        width = (config \ "width").as[Int]
        height = (config \ "height").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Evaluate title and content
            val evalContent = utils.evaluateTuktuString(content, datum)
            
            addJsElement(datum, new WebJsCodeObject(
                    "var c=document.createElement('div');" +
                        "c.innerHTML='" + evalContent + "';" +
                        "c.id='divContent';" +
                        "c.style.position='fixed';" +
                        "c.style.border='1px solid gray';" +
                        "c.style.padding='10px';" +
                        "c.style.backgroundColor='white';" +
                        "c.style.width=" + width + "+'px';" +
                        "c.style.height=" + height + "+'px';" +
                        "c.style.bottom=0;" +
                        "c.style.right=0;" +
                        "c.style.zIndex=99999999;" +
                        "var b=document.getElementsByTagName('body')[0];" +
                        "b.appendChild(c);" +
                        "$('#divContent').show(1000);" +
                        "$(window).on('load resize scroll',function(e){" +
                        "$('#divContent').css('bottom','0');$('#divContent').css('right','0');" +
                        "});" +
                        "$(c).click(function () {" +
                          "$('#divContent').show(500);" +
                        "});"
            ))
        }
    })
}