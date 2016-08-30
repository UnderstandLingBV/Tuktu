package tuktu.csv.generators.flattening

import java.io.File
import java.io.FileInputStream
import scala.collection.JavaConversions.asScalaIterator
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import play.api.libs.json.JsArray
import tuktu.api.BaseGenerator

class XlsReader(parentActor: ActorRef, fileName: String, sheetName: String, valueName: String,
                dataColStart: Int, dataColEnd: Option[Int], hierarchy: List[ParseNode], endFieldCol: Int, endField: String) extends Actor with ActorLogging {

    private[this] def rowToList(row: Row): List[String] = {
        // Get the max column
        val maxColumn = row.getLastCellNum
        // Iterate over cells
        (for (col <- 0 to maxColumn) yield {
            val cell = row.getCell(col, Row.RETURN_BLANK_AS_NULL)
            if (cell == null) ""
            else {
                // Get the value
                cell.getCellType match {
                    case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BOOLEAN =>
                        cell.getBooleanCellValue.toString
                    case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC => {
                        // Double or int?
                        val originalValue = cell.getNumericCellValue
                        if (originalValue.toInt.toDouble == originalValue)
                            cell.getNumericCellValue.toInt.toString // Integer
                        else
                            cell.getNumericCellValue.toString // Double
                    }
                    case org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING =>
                        cell.getStringCellValue
                    case _ => ""
                }
            }
        }).toList
    }

    def receive() = {
        case ip: InitPacket => {
            // Open the workbook for reading
            val workbook = new HSSFWorkbook(new FileInputStream(new File(fileName)))
            // Get the proper sheet
            val sheet = workbook.getSheet(sheetName)
            val rowIterator = sheet.iterator

            // Start processing
            self ! new XlsReadPacket(workbook, rowIterator, 0)
        }
        case sp: XlsStopPacket => {
            // Close XLS reader, kill parent and self
            sp.workbook.close
            parentActor ! new StopPacket
        }
        case pkt: XlsReadPacket => pkt.reader.hasNext match {
            case false => self ! new XlsStopPacket(pkt.workbook) // EOF, stop processing
            case true => {
                // Get the row as a list of String
                val line = rowToList(pkt.reader.next)

                // See if this is the end or not
                if (line(endFieldCol) == endField) {
                    // We may need to stop preliminary if we find the end field
                    self ! new XlsStopPacket(pkt.workbook)
                } else {
                    val endPos = dataColEnd match {
                        case Some(end) => end
                        case None      => line.size - 1
                    }
                    // Go through cells of this row
                    for (i <- 0 to endPos) {
                        // Apply parsers on this cell
                        val flattenedMap = (for (pn <- hierarchy) yield {
                            pn.name -> pn.locator(line, pkt.rowOffset, i)
                        }).toMap

                        // Only do something if we have all values
                        if (i >= dataColStart && flattenedMap.forall(elem => elem._2 != null)) {
                            // Send back to parent
                            val mapToSend = (flattenedMap + (valueName -> line(i)))
                            parentActor ! mapToSend
                        }
                    }

                    // Continue with next line
                    self ! new XlsReadPacket(pkt.workbook, pkt.reader, pkt.rowOffset + 1)
                }
            }
        }
    }
}

class XlsGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    private var flattened = false

    override def _receive = {
        case config: JsValue => {
            // Get filename, sheet name and data start
            val fileName = (config \ "filename").as[String]
            val sheetName = (config \ "sheet_name").as[String]
            val valueName = (config \ "value_name").as[String]

            // Get hierarchy
            val hierarchy = Common.parseHierarchy((config \ "locators").as[List[JsObject]])

            // Flattened or not
            flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
            val dataColStart = (config \ "data_start_col").as[Int]
            val dataColEnd = (config \ "data_end_col").asOpt[Int]
            val endFieldCol = (config \ "end_field" \ "column").as[Int]
            val endField = (config \ "end_field" \ "value").as[String]

            // Create actor and kickstart
            val xslGenActor = Akka.system.actorOf(Props(classOf[XlsReader], self, fileName, sheetName, valueName,
                dataColStart, dataColEnd, hierarchy, endFieldCol, endField))
            xslGenActor ! new InitPacket()
        }
        case headerfullLine: Map[String, String] => flattened match {
            case false => channel.push(new DataPacket(List(Map(resultName -> headerfullLine))))
            case true  => channel.push(new DataPacket(List(headerfullLine)))
        }
    }
}