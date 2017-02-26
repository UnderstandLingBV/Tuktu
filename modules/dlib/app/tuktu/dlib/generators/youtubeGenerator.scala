package tuktu.dlib.generators

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicInteger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{ JsObject, JsValue }
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._
import tuktu.dlib.utils.youtube

case class VideosPacket( metadata: Seq[JsObject] )
case class VideoPacket( video: JsObject )

/**
 * Actor that queries the Youtube Data API
 */
class YoutubeActor(parentActor: ActorRef, query: String) extends Actor with ActorLogging 
{
    def receive() = 
    {   
        case ip: InitPacket => youtube.callYoutube( query ).map( metadata => self ! VideosPacket( metadata )  )
        
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case vpacket: VideosPacket => 
        {
            // Send back to parent for pushing into channel
            parentActor ! VideoPacket( vpacket.metadata.head )

            // Continue with remaining links if any
            vpacket.metadata.tail.isEmpty match {
              case true => self ! new StopPacket
              case false => self ! VideosPacket( vpacket.metadata.tail )
            }
        }
    }
}

/**
 * Queries the Youtube API and returns pointers to the resulting records.
 */
class YoutubeGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    var flatten: Boolean = _  
  
    override def _receive = 
    {
      
        case config: JsValue => 
        {
            // Get the Youtube API parameters        
            val api = (config \ "api").asOpt[String].getOrElse("https://www.googleapis.com/youtube/v3/search")
            val apikey = (config \ "apikey").as[String]
            val part = (config \ "part").asOpt[String].getOrElse("snippet") 
            val channelId = (config \ "channelId").asOpt[String]
            val maxResults = (config \ "maxResults").asOpt[Int] // must be between 0 and 50
            val order = (config \ "order").asOpt[String]
            val query = (config \ "query").asOpt[String]
            val topicId = (config \ "topicId").asOpt[String]
            val youtype = (config \ "type").asOpt[String].getOrElse("video")
            val videoLicense = (config \ "license").asOpt[String].getOrElse("any")
            flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
            
            // https://www.googleapis.com/youtube/v3/search?part=snippet&q=test&type=video&key=AIzaSyCAeNFAL5bLiVWahLR7nntNR7fRWl7OGyQ
            
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
            
            youtube.callYoutube( url )
            // Create actor and kickstart
            val youtubeActor = Akka.system.actorOf(Props(classOf[YoutubeActor], self, url ))
            youtubeActor ! new InitPacket()
        }
        case video: VideoPacket => {
            if ( flatten ) 
            {
                channel.push( DataPacket( List( utils.JsObjectToMap( video.video ) ) ) )
            } 
            else
            {
                channel.push( DataPacket( List( Map( resultName -> video.video ) ) ) )
            }
        }
    }
}

