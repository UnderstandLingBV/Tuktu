package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Writes specific fields of the datapacket out to HDFS, by default as JSON
 */
class HDFSWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fileName: Path = _
    var fields: List[String] = _
    var fieldSeparator = ""
    var dataPacketSeparator = ""
    var conf: Configuration = _
    
    override def initialize(config: JsObject) = {
        val hdfsUri = (config \ "uri").as[String]
        fileName = new Path((config \ "file_name").as[String])
        fields = (config \ "fields").as[List[String]]
        fieldSeparator = (config \ "field_separator").as[String]
        dataPacketSeparator = (config \ "datapacket_separator").as[String]
        conf = new Configuration()
        conf.set("fs.defaultFS", hdfsUri)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val fs = FileSystem.get(conf)
        val os = {            
            if(fs.exists(fileName)) 
                fs.append(fileName)
            else
                fs.create(fileName)
        }
        for (datum <- data.data) {
            for (field <- fields) {
                os.write((datum(field).toString + fieldSeparator).getBytes)
            }
            os.write(dataPacketSeparator.getBytes)    
        }
        os.close
        fs.close
        
        data
    })

}