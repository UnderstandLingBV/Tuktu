package tuktu.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

import scala.io.Codec
import scala.io.Source

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import com.netaporter.uri.Uri

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.mvc.RequestHeader

object util {
    /**
     * Recursively traverses a path of keys until it finds a value (or fails to traverse,
     * in which case a default value is used)
     */
    def fieldParser(input: Map[String, Any], path: List[String], defaultValue: Option[Any]): Any = path match {
        case someKey::List() => input(someKey)
        case someKey::trailPath => {
            // Get the remainder
            if (input.contains(someKey)) {
                // See if we can cast it
                try {
                    fieldParser(input(someKey).asInstanceOf[Map[String, Any]], trailPath, defaultValue)
                } catch {
                    case e: ClassCastException => defaultValue.getOrElse(null)
                }
            } else {
                // Couldn't find it
                defaultValue.getOrElse(null)
            }
        }
    }
    
    /**
     * Recursively traverses a JSON object of keys until it finds a value (or fails to traverse,
     * in which case a default value is used)
     */
    def jsonParser(json: JsValue, jsPath: List[String], defaultValue: Option[JsValue]): JsValue = jsPath match {
        case List() => json
        case js::trailPath => {
            // Get the remaining value from the json
            val newJson = (json \ js).asOpt[JsValue]
            newJson match {
                case Some(nj) => {
                    // Recurse into new JSON
                    jsonParser(nj, trailPath, defaultValue)
                }
                case None => {
                    // Couldn't find it, return the best we can
                    defaultValue match {
                        case Some(value) => value
                        case _ => json
                    }
                }
            }
        }
    }
    
    /**
     * Turns a JSON string (with enclosing quotes) into a normal string (with no quotes)
     */
    def JsonStringToNormalString(value: JsString) = {
        // Remove the annoying quotes
       value.toString.drop(1).take(value.toString.size - 2)
    }
    
    /**
     * Converts a flash from an HTTP request into a map of messages
     */
    def flashMessagesToMap(request: Request[AnyContent]) = {
        (
            request.flash.get("error") match {
                case Some(error) => Map(
                        "errors" -> error.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("success") match {
                case Some(success) => Map(
                        "success" -> success.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("info") match {
                case Some(info) => Map(
                        "info" -> info.split(";").toList
                )
                case _ => Map()
            }
        )
    }
    
    // A Generic Reader for multiple sources
    def genericReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        uri.getScheme match {
            case "file" | "" | null => fileReader(uri)
            case "hdfs" => hdfsReader(uri)                        
            case _ => throw new Exception ("Unknown file format")
        }
    }
    
    def genericReader(string: String)(implicit codec: Codec): BufferedReader = {
        genericReader(Uri.parse(string).toURI)(codec)
    }
    
    
    /**
     * Reads from Local disk
     */
    def fileReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        Source.fromFile(uri.getPath)(codec).bufferedReader
    }

    /**
     * Reads from HDFS
     */
    def hdfsReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        val conf = new Configuration
        conf.set("fs.defaultFS", uri.getHost + ":" + uri.getPort)        
        val fs = FileSystem.get(conf)
        val path = new Path(uri.getPath)
        new BufferedReader(new InputStreamReader(fs.open(path),codec.decoder))        
    }
    
    
}