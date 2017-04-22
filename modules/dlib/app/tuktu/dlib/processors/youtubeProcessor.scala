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
import tuktu.dlib.utils.youtube

class YoutubeProcessor(resultName: String) extends BaseProcessor(resultName) 
{       
    var api: String = _
    var apikey: String = _
    var part: String = _
    var channelId: Option[String] = _
    var maxResults: Option[Int] = _
    var order: Option[String] = _
    var query: Option[String] = _
    var topicId: Option[String] = _
    var youtype: String = _
    var videoLicense: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the Youtube API parameters        
        api = (config \ "api").asOpt[String].getOrElse("https://www.googleapis.com/youtube/v3/search")
        apikey = (config \ "apikey").as[String]
        part = (config \ "part").asOpt[String].getOrElse("snippet") 
        channelId = (config \ "channelId").asOpt[String]
        maxResults = (config \ "maxResults").asOpt[Int] // must be between 0 and 50
        order = (config \ "order").asOpt[String]
        query = (config \ "query").asOpt[String]
        topicId = (config \ "topicId").asOpt[String]
        youtype = (config \ "type").asOpt[String].getOrElse("video")
        videoLicense = (config \ "license").asOpt[String].getOrElse("any")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => 
    {
        val url =  api + "?part=" + part +  (channelId match {
              case None => ""
              case Some(channel) => "&channelId=" + channel 
            }) + ( maxResults match { 
              case None => ""
              case Some(max) => "&maxResults=" + max.toString
            } ) + ( order match {
              case None => ""
              case Some(ord) => "&order=" + ord
            }) + ( query match {
              case None => ""
              case Some(q) => "&q=" + q
            }) + (topicId match {
              case None => ""
              case Some(topic) => "&topicId=" + topic
            }) + "&type=" + youtype + "&videoLicense=" + videoLicense + "&key=" + apikey
        val listfuture = data.data.map{ datum => 
            val futureresult = youtube.callYoutube( url )
            futureresult.map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => DataPacket( fl ) }
    })
}