package tuktu.dlib.processors.oaipmh

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api._
import play.api.libs.json._
import play.api.cache.Cache
import scala.collection.mutable.ListBuffer
import tuktu.dlib.utils._

/**
 * Harvests metadata records from an OAI-PMH target repository.
 */
class ListRecordsProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Set up the packet sender actor
    val packetSenderActor = Akka.system.actorOf(Props(classOf[PacketSenderActor], genActor))
    
    var target: String = _
    var metadataPrefix: String = _
    var from: Option[String] = _
    var until: Option[String] = _
    var set: Option[String] = _
    var toj: Boolean = _
    
    override def initialize(config: JsObject) 
    {
        target = (config \ "target").as[String]
        metadataPrefix = (config \ "metadataPrefix").as[String]
        from = (config \ "from").asOpt[String]
        until = (config \ "until").asOpt[String]
        set = (config \ "set").asOpt[String]
        toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
    }
    
    
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val futures = Future.sequence( 
            (for (datum <- data.data) yield
            {
               // compose verb
               val verb = utils.evaluateTuktuString(target, datum) + "?verb=ListRecords"
               // compose params
               val params = "&metadataPrefix=" + utils.evaluateTuktuString(metadataPrefix, datum) + (from match{
                  case None => ""
                  case Some(f) => "&from=" + utils.evaluateTuktuString(f, datum)
                }) + (until match{
                  case None => ""
                  case Some(u) => "&until=" + utils.evaluateTuktuString(u, datum)
                }) + (set match{
                  case None => ""
                  case Some(s) => "&set=" + utils.evaluateTuktuString(s, datum)
                })
              // harvest
              listRecords( verb, params, datum )
            }).flatMap { x => x }
        )
        
        futures.map {
            case _ => data
        }
    }) compose Enumeratee.onEOF(() => packetSenderActor ! new StopPacket)
    
    def listRecords( verb: String, params: String, datum: Map[String, Any] ): Seq[Future[Any]] =
    {
        val response = oaipmh.harvest( verb + params )
        // check for error
      (response \\ "error").headOption match
      {
        case None => {
          // extract records and send them to channel
          val records = (response \ "ListRecords" \ "record" \ "metadata" ).flatMap( _.child ).toSeq
          val recs = (records map { rec => rec.toString.trim })
          val result = for (record <- recs; if (!record.isEmpty)) yield
          {
            toj match{
              case false => packetSenderActor ? (datum + ( resultName -> record ))
              case true => packetSenderActor ? (datum + ( resultName -> oaipmh.xml2jsObject( record ) ))
            }
          }
          // check for resumption token
          val rToken = ( response \ "ListRecords" \ "resumptionToken" ).headOption
          rToken match
            {
              case Some( resumptionToken ) => result ++ listRecords( verb, ("&resumptionToken=" + resumptionToken.text), datum ) // keep harvesting
              case None => result // harvesting completed
            }
        }  
        case Some( err ) => {
          toj match{
              case false => Seq(packetSenderActor ? (datum + ( resultName -> response.toString )))
              case true => Seq(packetSenderActor ? (datum + ( resultName -> oaipmh.xml2jsObject( response.toString ) )))
            }
        }
          
      }
    }
}

/**
 * Actor for forwarding the split data packets
 */
class PacketSenderActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    remoteGenerator ! new InitPacket
    
    def receive() = {
        case sp: StopPacket => {
            remoteGenerator ! sp
            self ! PoisonPill
        }
        case datum: Map[String, Any] => {
            // Directly forward
            remoteGenerator ! new DataPacket(List(datum))
            sender ! "ok"
        }
    }
}