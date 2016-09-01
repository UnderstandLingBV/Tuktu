package tuktu.dlib.processors


import akka.util.Timeout

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

import play.api.libs.ws.WS
import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.utils.lre


/**
 * Retrieves Learning Resource Exchange (LRE) metadata and paradata records based on their identifiers.
 */
class LRERetrievalProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var service: String = _
    var identifiers: String = _
    var format: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the LRE query parameters
        service = (config \ "service").asOpt[String].getOrElse( "http://lredata.eun.org/" )
        identifiers = (config \ "identifiers").as[String]
        format = (config \ "format").asOpt[String].getOrElse("json")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => 
    {
        val listfuture = data.data.map{ datum => 
            val request = evaluateTuktuString(service, datum) + "?format=" + evaluateTuktuString(format, datum) + "&ids=" + evaluateTuktuString(identifiers, datum)
            val futureresult = WS.url(request).get.map { response => response.json }
            futureresult.map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => DataPacket( fl ) }
    })
}