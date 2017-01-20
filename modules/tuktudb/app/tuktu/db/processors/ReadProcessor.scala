package tuktu.db.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._

/**
 * Reads a data packet from the Tuktu DB
 */
class ReadProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var key: String = _

    override def initialize(config: JsObject) {
        key = (config \ "key").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        if (data.nonEmpty) {
            // Parse keys
            val evalKey = utils.evaluateTuktuString(key, data.data.head)

            // Request value from daemon
            val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new ReadRequest(evalKey, true)

            fut.map {
                case rr: ReadResponse => DataPacket(rr.value)
            }
        } else Future { data }
    })
}

/**
 * Gets the names of buckets that satisfy a given pattern
 */
class GetBucketsProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var pattern: String = _
    var flatten: Boolean = _

    override def initialize(config: JsObject) {
        pattern = (config \ "pattern").as[String]
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Get the buckets present
        val buckets = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new OverviewRequest(-1)
        
        buckets.map {
            case or: OverviewReply => {
                val names = or.bucketCounts.map(_._1).toList
                
                // See if we need to flatten
                if (flatten)
                    new DataPacket(
                            names.filter(_.matches(pattern)).map {name =>
                                Map(resultName -> name)
                            } toList
                    )
                else 
                    new DataPacket(data.data.map {datum =>
                        datum + (resultName -> names.filter {name =>
                            name.matches(pattern)
                        })
                    })
            }
        }
    })
}