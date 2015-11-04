package tuktu.csv.generators.flattening

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import au.com.bytecode.opencsv.CSVReader
import play.api.Logger

case class ParseNode(
        name: String,
        locator: (List[String], Int, Int) => String
)

case class XlsReadPacket(
        workbook: HSSFWorkbook,
        reader: Iterator[Row],
        rowOffset: Int
)
case class XlsStopPacket(
        workbook: HSSFWorkbook
)

case class XlsxReadPacket(
        workbook: XSSFWorkbook,
        reader: Iterator[Row],
        rowOffset: Int
)
case class XlsxStopPacket(
        workbook: XSSFWorkbook
)

case class CSVReadPacket(
        reader: CSVReader,
        rowOffset: Int
)
case class CSVStopPacket(
        reader: CSVReader
)


object Common {
    /**
     * Parses hierarchy given in a JSON object to something more suitable
     * @param obj JsObject The hierarchy JSON object
     * @return List[ParseNode] The list of parse nodes to apply, in-order
     */
    def parseHierarchy(obj: List[JsObject]): List[ParseNode] ={
        for (root <- obj) yield {
            // Set up the locator
            val locatorType = (root \ "type").as[String]
            val locatorParams = (root \ "params").as[JsValue]
            val clazz = Class.forName(locatorType)
            val inst = try {
                clazz.getConstructor(classOf[JsValue]).newInstance(locatorParams)
            } catch {
                case e: java.lang.reflect.InvocationTargetException => {
                    Logger.error("Couldn't get class", e.getTargetException)                    
                    null
                }
            }
            val method = clazz.getMethods.filter(m => m.getName == "getFromCsvRow").head
            val proc = method.invoke(inst).asInstanceOf[(List[String], Int, Int) => String]
            
            // Create the node
            new ParseNode(
                    (root \ "name").as[String],
                    proc
            )
        }
    }
}