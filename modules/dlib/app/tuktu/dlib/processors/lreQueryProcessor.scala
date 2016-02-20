package tuktu.dlib.processors

import akka.util.Timeout

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.utils.lre


/**
 * Queries the LRE REST API and returns the identifiers of the resulting records.
 */
class LREQueryProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var service: String = _
    var cnf: String = _
    var limit: Option[Int] = _
    var resultOnly: Boolean = _
    
    override def initialize(config: JsObject) 
    {
        // Get the LRE query parameters
        service = (config \ "service").asOpt[String].getOrElse( "http://lresearch.eun.org/" )
        cnf = (config \ "query").as[String]
        limit = (config \ "limit").asOpt[Int]
        resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => 
    {
        val listfuture = data.data.map{ datum => 
            val query = evaluateTuktuString(service, datum) + "?cnf=" + evaluateTuktuString(cnf, datum) + (limit match {
                case Some(lmt) => "&limit=" + lmt
                case None => ""
            })
            val futureresult = lre.callLRE( query, resultOnly )
            futureresult.map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => new DataPacket( fl ) }
    })
}