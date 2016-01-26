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
            val futureresult = callLRE( query )
            futureresult.map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => new DataPacket( fl ) }
    })

    def callLRE(query: String): Future[Any] = 
    {
        val future = WS.url(query).get
        future.map(response => {
            val res = response.json
            (res \ "ids").asOpt[String] match
            {
                case None => res
                case Some(ids) => resultOnly match
                {
                    case false => res
                    case true => unroll( ids )
                }
            }
        })
    }
    
    def unroll( ids: String ): Seq[String] =
    {
        val rangeRegex = """\[\d+,\d+\]""".r
        val listRegex = """\{([^\}]+)\}""".r
        val ranges = rangeRegex.findAllMatchIn(ids).toList.flatMap{ range => expand( range.matched ) }
        val lists = listRegex.findAllMatchIn(ids).toList.flatMap{ list => """\d+""".r.findAllMatchIn(list.matched).toList.map{ el => el.matched } }
        (ranges ++ lists).sorted
    }
    
    def expand( range: String ): Seq[String] =
    {
      val regex = """\[(\d+),(\d+)\]""".r
      range match{
        case regex( min, max ) => Range( s"$min".toInt, s"$max".toInt ).map{ i => "" + i } 
        case _ => List()
      }
    }

}