package tuktu.nosql.generators

import tuktu.api.AsyncGenerator
import play.api.libs.json.JsValue
import tuktu.api._
import play.api.libs.iteratee.Enumeratee
import java.util.Properties
import kafka.consumer.Consumer
import kafka.consumer.ConsumerConfig
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.actors.threadpool.Executors

class KafkaGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
    override def receive() = {
        case config: JsValue => {
            // Get kafka properties
            val kafkaProps = new Properties
            val kafkaConfig = (config \ "kafka_properties").as[Map[String, String]]
            kafkaConfig foreach (kv => kafkaProps.put(kv._1, kv._2))

            // Create the connection to the cluster
            val consumerConfig = new ConsumerConfig(kafkaProps)
            val consumerConnector = Consumer.createJavaConsumerConnector(consumerConfig)

            // Get the topic
            val topic = (config \ "topic").as[String]
            // If defined, we can use a designated stopping packet that, once read from kafka, will kill the stream
            val stopMessage = (config \ "stop_message").asOpt[String]
            
            // See if we need to convert the message to string
            val convertToString = (config \ "to_string").asOpt[Boolean].getOrElse(true)
            val charset = (config \ "charset").asOpt[String].getOrElse("utf-8")

            // How many threads to read partitions?
            val threadCount = (config \ "threads").asOpt[Int].getOrElse(1)
            // Set up the streams to consume
            val topicMessageStreams = consumerConnector.createMessageStreams(
                mapAsJavaMap(Map(topic -> threadCount)).asInstanceOf[java.util.Map[String, Integer]])
            val streams = topicMessageStreams.get(topic)

            // Set up threadpool
            val executor = Executors.newFixedThreadPool(threadCount)

            // Start consuming
            for (stream <- streams) {
                executor.submit(new Runnable() {
                    def run() {
                        for (msgAndMetadata <- stream) {
                            // Get message
                            val message = convertToString match {
                                case true => new String(msgAndMetadata.message, charset)
                                case false => msgAndMetadata.message
                            }
                            
                            // Process message, stop if required
                            stopMessage match {
                                case Some(stopMsg) => {
                                    if (message == stopMsg) {
                                        // We need to terminate
                                        executor.shutdown
                                        consumerConnector.shutdown
                                        self ! StopPacket
                                    }
                                    else channel.push(new DataPacket(List(Map(resultName -> message))))
                                }
                                case None => channel.push(new DataPacket(List(Map(resultName -> message))))
                            }
                        }
                    }
                })
            }
        }
        case sp: StopPacket => {
            cleanup()
        }
    }
}