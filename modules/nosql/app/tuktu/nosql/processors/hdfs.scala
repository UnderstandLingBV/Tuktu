package tuktu.nosql.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Writes specific fields of the datapacket out to HDFS, by default as JSON
 */
class HDFSWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var hdfsUri = ""
    var fileName = ""
    var fields: List[String] = _
    var outputFormat = ""
    
    override def initialize(config: JsObject) = {
        hdfsUri = (config \ "uri").as[String]
        fileName = (config \ "file_name").as[String]
        fields = (config \ "fields").as[List[String]]
        outputFormat = (config \ "format").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        /*Configuration configuration = new Configuration();
        FileSystem hdfs = FileSystem.get( new URI(hdfsUri), configuration );
        Path file = new Path(hdfsUri + "/" + fileName);
        if ( hdfs.exists( file )) { hdfs.delete( file, true ); } 
        OutputStream os = hdfs.create( file,
            new Progressable() {
                public void progress() {
                    out.println("...bytes written: [ "+bytesWritten+" ]");
                } });
        BufferedWriter br = new BufferedWriter( new OutputStreamWriter( os, "UTF-8" ) );
        br.write("Hello World");
        br.close();
        hdfs.close();*/
        data
    })
}