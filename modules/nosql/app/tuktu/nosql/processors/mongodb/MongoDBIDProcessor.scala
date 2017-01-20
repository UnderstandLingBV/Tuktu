package tuktu.nosql.processors.mongodb

import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

/**
 * Adds a mongo-compatible ID
 */
class MongoDBIDProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(data.data.map {datum =>
            datum + (resultName -> BSONObjectID.generate.stringify)
        })
    })
}