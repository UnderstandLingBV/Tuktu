package tuktu.web.processors.analytics

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.http.client.utils.URLEncodedUtils
import scala.annotation.meta.param
import org.apache.http.NameValuePair
import java.net.URI
import scala.annotation.meta.param
import scala.collection.JavaConversions._
import java.net.MalformedURLException

/**
 * Parses a URL and adds a map with key/value pairs
 */
class URLParserProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get URL 
            try {
                val url = new URL(datum(field).asInstanceOf[String])
                datum + (resultName -> Map(
                    "protocol " -> url.getProtocol,
                    "authority " -> url.getAuthority,
                    "host " -> url.getHost,
                    "port " -> url.getPort,
                    "path " -> url.getPath,
                    "query " -> url.getQuery,
                    "filename " -> url.getFile,
                    "ref " -> url.getRef))
            } catch {
                case e: MalformedURLException => datum + (resultName -> Map.empty[String, String])
            }
        })
    })
}

/**
 * Parses a query string of a URL
 */
class URLQueryStringParserProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get URL 
            val url = new URI(datum(field).asInstanceOf[String])
            val params = URLEncodedUtils.parse(url, "UTF-8")

            datum + (resultName -> params.map(nvp => nvp.getName -> nvp.getValue))
        })
    })
}