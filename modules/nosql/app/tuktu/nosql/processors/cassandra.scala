package tuktu.nosql.processors

import tuktu.api._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.nosql.util.cassandra
import scala.collection.JavaConversions._
import scala.concurrent.Future
import play.api.libs.json.JsObject

class CassandraProcessor(resultName: String) extends BaseProcessor(resultName) {
    var client: cassandra.client = null
    var append = false
    var query = ""

    override def initialize(config: JsObject) {
        // Get hostname
        val address = (config \ "address").as[String]
        // Initialize client
        client = new cassandra.client(address)

        // Get the query
        query = (config \ "query").as[String]

        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Evaluate query
            val evalQuery = utils.evaluateTuktuString(query, datum)

            // See if we need to append the result
            append match {
                case false => {
                    // No need for appending
                    client.runQuery(evalQuery)
                    datum
                }
                case true => {
                    // Get the result and use it
                    val res = client.runQuery(evalQuery).get.all.map(row => cassandra.rowToMap(row)).toList

                    datum + (resultName -> res)
                }
            }
        }
    }) compose Enumeratee.onEOF(() => {
        client.close
    })
}