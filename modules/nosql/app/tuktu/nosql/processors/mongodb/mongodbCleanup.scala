package tuktu.nosql.processors.mongodb

import collection.immutable.SortedSet
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }
import reactivemongo.core.nodeset.Authenticate
import play.modules.reactivemongo.json._
import scala.concurrent.Future
import tuktu.nosql.util._

import javax.inject.Inject

/**
 * Inserts data into MongoDB
 */
class MongoDBCleanupProcessor(resultName: String) extends BaseProcessor(resultName) {

      override def initialize(config: JsObject) {}

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future(data)) compose Enumeratee.onEOF { () => mongoTools.deleteAllCollections }
}