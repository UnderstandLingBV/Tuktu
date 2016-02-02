package tuktu.web.processors

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import play.api.libs.ws.WS

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString

/**
 * Checks a URL status code.
 */
class URLCheckerProcessor(resultName: String) extends BaseProcessor(resultName) 
{

    var url: String = _
    var codes: Option[List[Int]] = _

    override def initialize(config: JsObject) {
        // Get the name of the url to check.
        url = (config \ "url").as[String]
        // Get the codes to consider as valid.
        codes = (config \ "codes").asOpt[List[Int]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val lfuture = data.data.map{ datum => 
            val u = evaluateTuktuString(url, datum)
            val code = checkUrl( u )
            codes match {
                case None => code.map { c => datum + (resultName -> c)}
                case Some( cs ) => code.map { c => datum + (resultName -> cs.contains(c)) }
            }
        }
        Future.sequence( lfuture ).map{ fl => new DataPacket( fl ) }
    })

    def checkUrl(url: String): Future[Int] = {
        WS.url( url ).head().map( response => response.status ).recover{
          case ce: java.net.ConnectException => -1
        }
    }
}