package tuktu.social.generators

import tuktu.api._
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import twitter4j.json.DataObjectFactory
import akka.actor.PoisonPill
import akka.actor.ActorRef

class TwitterGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
	override def receive() = {
	     case config: JsValue => {
	         // Get credentials
	         val credentials = (config \ "credentials").as[JsObject]
	         val consumerKey = (credentials \ "consumer_key").as[String]
	         val consumerSecret = (credentials \ "consumer_secret").as[String]
	         val accessToken = (credentials \ "access_token").as[String]
	         val accessTokenSecret = (credentials \ "access_token_secret").as[String]
	         
	         // Get filters
	         val filters = Common.getFilters(config)
	         val keywords = filters("keywords").asInstanceOf[Array[String]]
	         val userids = filters("userids").asInstanceOf[Array[String]].map(elem => elem.toLong)
	         val geo = filters("geo").asInstanceOf[Array[Array[Double]]]
	         
	         // Implement the Twitter4J status listener which determines what to listen for
	         val listener: StatusListener = new StatusListener() {
	            @Override
	            def onStatus(status: Status): Unit = {
	                // Flatten the status and push it on
	                val flatStatus = Json.parse(DataObjectFactory.getRawJSON(status))
	                channel.push(new DataPacket(List(Map(resultName -> flatStatus))))
	            }
	
	            @Override
	            def onDeletionNotice(sdn: StatusDeletionNotice): Unit = {}
	
	            @Override
	            def onTrackLimitationNotice(i: Int): Unit = {}
	
	            @Override
	            def onScrubGeo(l: Long, l1: Long): Unit = {}
	
	            @Override
	            def onException(e: Exception): Unit = {
                 e.printStackTrace() 
              }
	            
	            @Override
	            def onStallWarning(warning: StallWarning): Unit = {
                 println(warning) 
              }
	        }
	         
	        //System.setProperty ("twitter4j.loggerFactory", "twitter4j.internal.logging.NullLoggerFactory")
	        // Create a Twitter4J configuration to pass on the OAuth credentials
	        val cb = new ConfigurationBuilder().
	            setDebugEnabled(true).
	            setJSONStoreEnabled(true).
	            setOAuthConsumerKey(consumerKey).
	            setOAuthConsumerSecret(consumerSecret).
	            setOAuthAccessToken(accessToken).
	            setOAuthAccessTokenSecret(accessTokenSecret)
	        // Create a Twitter4J instance with the OAuth credentials and the listener
	        val fact = new TwitterStreamFactory(cb.build)
	        val twitterStream = fact.getInstance
	        twitterStream.addListener(listener)
	
	        // Add the filters (otherwise we get the sample)
	        val fq = new FilterQuery()
	        if (keywords != null && !keywords.isEmpty)
	        	fq.track(keywords)
	        if (userids != null && !userids.isEmpty)
	            fq.follow(userids)
	        if (geo != null && !geo.isEmpty)
	            fq.locations(geo)
	        	
	        // Add the query
	        twitterStream.filter(fq)
	    }
        case sp: StopPacket => {
            cleanup()
        }
	 }
}