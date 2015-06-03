package tuktu.dfs.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ReaderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var filename = ""
    
    override def initialize(config: JsObject) = {
        // Get file name
        filename = (config \ "filename").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // We first resolve all filenames
        val tuktuFilenames = data.data.map(datum => utils.evaluateTuktuString(filename, datum)).distinct
        
        // See if we can get the files
        for (name <- tuktuFilenames) yield {
            
        }
        
        data
    })
}