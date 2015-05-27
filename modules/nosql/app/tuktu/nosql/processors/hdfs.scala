package tuktu.nosql.processors

import java.io.BufferedWriter
import java.io.OutputStreamWriter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FSDataOutputStream
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
    // The filename to write to
    var fileName: String = _
    // The fields used to write to HDFS
    var fields: List[String] = _
    // The separator between fields
    var fieldSeparator = ""
    // The separator between DataPackets
    var dataPacketSeparator = ""
    // The HDFS configuration file
    var conf: Configuration = _
    
    var encoding = ""
    
    override def initialize(config: JsObject) = {
        val hdfsUri = (config \ "uri").as[String]
        fileName = (config \ "file_name").as[String]
        fields = (config \ "fields").as[List[String]]
        fieldSeparator = (config \ "field_separator").as[String]
        dataPacketSeparator = (config \ "datapacket_separator").as[String]
        
        // The replication factor of the file
        val replication = (config \ "replication").asOpt[Int].getOrElse(3)
        conf = new Configuration
        conf.set("fs.defaultFS", hdfsUri)
        conf.set("dfs.replication", replication.toString)
        
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {        
        val fs = FileSystem.get(conf)

        // Create a Map containing all the writers
        val writers = scala.collection.mutable.Map[String, (FSDataOutputStream, BufferedWriter)]()
        
        for (datum <- data.data) {
            val name = tuktu.api.utils.evaluateTuktuString(fileName, datum)
            
            // Set up the output stream reader and buffered writer
            val (os, bw) = {
                if(writers.contains(name)) {
                    writers(name)
                }
                else {
                    val path = new Path(name)
                    
                    // Create or append the OutputStream, then make the writer
                    val fsdos = {
                        if (fs.exists(path)) fs.append(path)
                        else fs.create(path)
                    }
                    val writer = new BufferedWriter(new OutputStreamWriter(fsdos, encoding))
                    writers += name -> (fsdos, writer)
                    
                    writers(name)
                }
            }
            
            // Write out everything properly
            fields.foreach(field => bw.write(datum(field).toString + fieldSeparator))
            bw.write(dataPacketSeparator)    
        }
        
        // close all writers
        writers.foreach(elem => {
            elem._2._2.close
            elem._2._1.close
        })
        //close filesystem
        fs.close
        
        data
    })

}