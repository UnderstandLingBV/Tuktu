package tuktu.db.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import play.api.cache.Cache
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import akka.actor.Identify
import akka.actor.ActorIdentity
import scala.concurrent.Await

/**
 * Writes a data packet to the in-memory DB
 */
class WriteProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var key: String = _
    var sync: Boolean = _

    override def initialize(config: JsObject) {
        key = (config \ "key").as[String]
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        val store = new StoreRequest(for (datum <- data.data) yield {
            val evalKey = utils.evaluateTuktuString(key, datum)
            new DBObject(evalKey, datum)
        }, sync)

        // Send request to daemon
        if (sync) {
            val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? store
            fut.map { _ => data }
        } else {
            Future {
                Akka.system.actorSelection("user/tuktu.db.Daemon") ! store
                data
            }
        }
    })
}