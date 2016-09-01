package tuktu.nosql.processors

import java.util.Properties

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import kafka.javaapi.producer.Producer
import kafka.producer.KeyedMessage
import kafka.producer.ProducerConfig
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Kafka producer
 */
class KafkaProcessor(resultName: String) extends BaseProcessor(resultName) {
    var producer: Producer[String, String] = _
    var keyField: String = _

    override def initialize(config: JsObject) {
        // Kafka properties
        val props = new Properties()
        val kafkaProps = (config \ "kafka_props").as[JsObject]
        for (key <- kafkaProps.keys)
            props.put(key, (kafkaProps \ key).as[String])

        // Initialize producer
        producer = new Producer[String, String](new ProducerConfig(props))

        (config \ "key_field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Send the data to kafka
        for (datum <- data) {
            // Get the key
            val key = datum(keyField).toString
            // Serialize datum
            val datumJson = Json.toJson(datum.map(elem => elem._1 -> elem._2.toString))

            val message = new KeyedMessage[String, String](key, datumJson.toString)
            producer.send(message)
        }

        Future { data }
    }) compose Enumeratee.onEOF(() => {
        producer.close
    })
}