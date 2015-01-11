package tuktu.nosql.processors

import tuktu.api._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import kafka.javaapi.producer.Producer
import kafka.producer.KeyedMessage
import kafka.producer.ProducerConfig
import play.api.libs.json.JsObject
import java.util.Properties
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

/**
 * Kafka producer
 */
class KafkaProcessor(resultName: String) extends BaseProcessor(resultName) {
    var producer: Producer[String, String] = null
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Initialize producer
        if (producer == null) {
            // Kafka properties
            val props = new Properties()
            val kafkaProps = (config \ "kafka_props").as[JsObject]
            for (key <- kafkaProps.keys) 
                props.put(key, (kafkaProps \ key).as[String])
                
            // Initialize producer
            producer = new Producer[String, String](new ProducerConfig(props))
        }
        
        // Send the data to kafka
        for (datum <- data.data) {
            // Get the key
            val key = datum((config \ "key_field").as[String]).toString
            // Serialize datum
            val datumJson = Json.toJson(datum.map(elem => elem._1 -> elem._2.toString))
            
            val message = new KeyedMessage[String, String](key, datumJson.toString)
            producer.send(message)
        }
        
        data
    }) compose Enumeratee.onEOF(() => {
        producer.close
    })
}