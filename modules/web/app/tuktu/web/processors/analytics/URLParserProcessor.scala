package tuktu.web.processors.analytics

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.{ BaseProcessor, DataPacket }
import java.net.URL
import java.net.MalformedURLException
import org.apache.http.client.utils.URLEncodedUtils
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

/**
 * Parses a URL and adds a map with key/value pairs
 */
class URLParserProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get URL 
            try {
                val url = new URL(datum(field).asInstanceOf[String])
                datum + (resultName -> Map(
                    "protocol" -> url.getProtocol,
                    "authority" -> url.getAuthority,
                    "host" -> url.getHost,
                    "port" -> url.getPort,
                    "path" -> url.getPath,
                    "query" -> url.getQuery,
                    "filename" -> url.getFile,
                    "ref" -> url.getRef))
            } catch {
                case e: MalformedURLException => datum + (resultName -> Map.empty[String, String])
            }
        }
    })
}

/**
 * Parses a query string of a URL
 */
class URLQueryStringParserProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var flatten: Boolean = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get URL
            val params = URLEncodedUtils.parse(datum(field).asInstanceOf[String], java.nio.charset.StandardCharsets.UTF_8)

            datum ++ (
                if (flatten) params.map(nvp => nvp.getName -> nvp.getValue).toMap
                else Map(resultName -> params.map(nvp => nvp.getName -> nvp.getValue).toList))
        }
    })
}