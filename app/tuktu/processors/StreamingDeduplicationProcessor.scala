package tuktu.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Deduplicates in a stream, meaning that only previously unseen data packets are forwarded
 */
class StreamingDeduplicationProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _
    val seenRecords = collection.mutable.ArrayBuffer[List[Any]]()

    override def initialize(config: JsObject) {
        // Get the field to sort on
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket((for {
            datum <- data.data

            // Get the fields we need to check upon
            key = fields.map(field => datum(field)).toList

            // Check if this one has been seen yet
            if (!seenRecords.contains(key))
        } yield {
            seenRecords += key
            datum
        }).toList)
    })
}