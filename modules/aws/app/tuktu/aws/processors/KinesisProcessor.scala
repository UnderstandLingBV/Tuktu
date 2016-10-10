package tuktu.aws.processors

import java.nio.ByteBuffer

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.regions._
import com.amazonaws.services.kinesis.model.PutRecordRequest
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{JsObject, JsValue}
import tuktu.api.{BaseProcessor, DataPacket}
import tuktu.api.utils
import tuktu.api.utils._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dev on 27-9-16.
  */
class KinesisProcessor(resultName: String) extends BaseProcessor(resultName) {

  var amazonKinesisClient: AmazonKinesisClient = _
  var partitionKey: Option[String] = _
  var streamName: String = _

  override def initialize(config: JsObject): Unit = {

    val url = (config\"url").as [String]
    val executionTimeout = (config\"execution_timeout").asOpt [Int].getOrElse(30000)
    val requestTimeout = (config\"request_timeout").asOpt [Int].getOrElse(30000)

    partitionKey = (config\"partition_key").asOpt [String]
    streamName = (config\"stream_name").as [String]

    amazonKinesisClient = new AmazonKinesisClient(
      new ClientConfiguration()
        .withClientExecutionTimeout(executionTimeout)
        .withRequestTimeout(requestTimeout))
    amazonKinesisClient.setRegion(Region.getRegion(Regions.EU_WEST_1))
    amazonKinesisClient.setEndpoint(url)
  }

  override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
    for (datum <- data) {
      amazonKinesisClient.putRecord({
        val request = new PutRecordRequest()
          .withStreamName(streamName)
        partitionKey match {
          case Some(key) => request.withPartitionKey(key)
          case None => {}
        }
        request.withData(ByteBuffer.wrap(utils.AnyToJsValue(datum).toString().getBytes))
      })
    }
    data
  })

}
