package tuktu.web.processors

import akka.util.Timeout

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import play.api.libs.ws.WS

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import scala.Stream

import tuktu.api._
import tuktu.api.BaseProcessor

/**
 * @author dmssrt
 */
class HTTPGetProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var field: String = _
    var timeout: Option[Int] = None
    
    override def initialize(config: JsObject) 
    {
        // Get the name of the field containing the URL to request and the request timeout        
        field = (config \ "field").as[String]  
        timeout = (config \ "timeout").asOpt[Int]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
                val urls: Stream[String] = datum( field ) match {
                    case u: String      => Stream( u ) 
                    case u: JsString    => Stream( u.value )
                    case s: Stream[Any] => s.map{ u => u match { case v: String => v; case v: JsString => v.value; case v: Any => v.toString() } }
                    case a: Any         => Stream( a.toString )
                }
                datum + ( resultName -> processURLs( urls ) )
            })
    })  
      
    def processURLs( urls: Stream[String] ): Stream[JsValue] =
    {
        if ( urls.tail.isEmpty )
        {
          return Stream( callService( urls.head ) )
        }
        else
        {
          return processURLs( urls.tail ) :+ callService( urls.head )
        }
    }

    def callService( url: String ): JsValue =
    {
        val duration: Duration = timeout match{
          case None => Duration( 30, SECONDS )
          case Some( length ) => Duration( length, SECONDS )
        }
        val future = WS.url( url ).get
        val body = future.map{ response => response.body }
        val result = Await.result( body, duration )
        return Json.parse( result )
    }
    
}