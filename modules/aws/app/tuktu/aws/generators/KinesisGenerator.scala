package tuktu.aws.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.amazonaws.auth._

import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.json.JsValue
import tuktu.api._
import tuktu.api.TuktuAWSCredentialProvider
import com.amazonaws.services.kinesis.model.Record
import com.google.common.base.Charsets
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory
import scala.collection.JavaConverters._
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownReason
import com.amazonaws.services.kinesis.clientlibrary.exceptions._
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor

/**
 * Reads data from a Kinesis stream
 */
class KinesisGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var worker: Worker = _
    
    override def _receive = {
        case config: JsValue => {
            // Get the consumer, app and stream name
            val streamName = (config \ "stream_name").as[String]
            val appName = (config \ "app_name").as[String]
            
            // Initial position
            val initialPosition = (config \ "initial_position").asOpt[String].getOrElse("latest")

            // Get retry count, backoff time and checkpoint interval 
            val retryCount = (config \ "retry_count").asOpt[Int].getOrElse(3)
            val backoffTime = (config \ "backoff_time").asOpt[Long].getOrElse(1000L)
            val checkpointInterval = (config \ "checkpoint_interval").asOpt[Long].getOrElse(1000L)

            // Optionally get AWS credentials?
            val credentials = ((config \ "aws_access_key").asOpt[String], (config \ "aws_access_secret").asOpt[String]) match {
                case (Some(awsAccessKey), Some(awsAccessSecret)) =>
                    new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsAccessSecret))
                case _ => new ProfileCredentialsProvider()
            }

            // Set up configuration
            val kclConfig = new KinesisClientLibConfiguration(appName, streamName,
                        credentials, java.util.UUID.randomUUID.toString)
            // Set initial position
            initialPosition match {
                case "horizon" => kclConfig.withInitialPositionInStream(InitialPositionInStream.TRIM_HORIZON)
                case _ => kclConfig.withInitialPositionInStream(InitialPositionInStream.LATEST)
            }
            
            // Create the processor factory
            val factory = new ProcessorFactory(self, retryCount, backoffTime, checkpointInterval)
            // Set up the worker
            worker = new Worker(factory, kclConfig)
            
            // Start the worker
            worker.run
        }
        case dp: DataPacket => channel.push(dp)
        case sp: StopPacket => {
            // Shut down our worker
            worker.shutdown
            cleanup
        }
    }
}

/**
 * Processor factory that deals with processing the records
 */
class ProcessorFactory(generator: ActorRef, retryCount: Int, backoffTime: Long, checkpointInterval: Long) extends IRecordProcessorFactory {
    override def createProcessor(): IRecordProcessor = {
        new TuktuRecordProcessor(generator, retryCount, backoffTime, checkpointInterval)
    }
}

/**
 * Actual implementation of the record processor
 */
class TuktuRecordProcessor(generator: ActorRef, retryCount: Int, backoffTime: Long, checkpointInterval: Long) extends IRecordProcessor {
    // Keep track of our checkpointing time
    var nextCheckpointTime: Long = 0
    
    override def initialize(shardId: String) {}
    
    /**
     * Processes records
     */
    override def processRecords(records: java.util.List[Record], checkpointer: IRecordProcessorCheckpointer) {
        // Process records and perform all exception handling
        processRecordsWithRetries(records.asScala.toList)

        // Checkpoint once every checkpoint interval
        if (System.currentTimeMillis > nextCheckpointTime) {
            checkpoint(checkpointer)
            nextCheckpointTime = System.currentTimeMillis + checkpointInterval
        }
    }
    
    /**
     * Processes records with retries
     */
    def processRecordsWithRetries(records: List[Record]) = {
        // Go over our records
        records.foreach(record => {
            // Process the record with retry
            processRecordWithRetries(record, 0)
        })
    }
    
    /**
     * Processes a single record with retries
     */
    def processRecordWithRetries(record: Record, attempt: Int): Boolean = {
        if (attempt >= retryCount) false
        else {
            try {
                processRecord(record)
                true
            }
            catch {
                case e: Throwable => {
                    Thread.sleep(backoffTime)
                    processRecordWithRetries(record, attempt + 1)
                }
            }
        }
    }
    
    /**
     * Processes the actual record to turn it into a DataPacket
     */
    def processRecord(record: Record) = {
        // Get the JSON data
        val json = Json.parse(new String(record.getData.array(), Charsets.UTF_8))
        // Determine what to do
        json match {
            // Send each element to our generator
            case arr: JsArray => new DataPacket(arr.value.toList.map(el => utils.JsObjectToMap(el.as[JsObject])))
            case el: JsObject => new DataPacket(List(utils.JsObjectToMap(el.as[JsObject])))
        }
    }
    
    /**
     * Upon shutdown, we may need to checkpoint
     */
    override def shutdown(checkpointer: IRecordProcessorCheckpointer, reason: ShutdownReason) {
        if (reason == ShutdownReason.TERMINATE)
            checkpoint(checkpointer)
    }
    
    /**
     * Does the actual checkpointing
     */
    def checkpoint(checkpointer: IRecordProcessorCheckpointer) {
        def checkpointWithRetries(num: Int): Boolean = {
            if (num >= retryCount) false
            else try {
                checkpointer.checkpoint
                true
            } catch {
                case e: ThrottlingException => {
                    Thread.sleep(backoffTime)
                    checkpointWithRetries(num + 1)
                }
                case e: ShutdownException => false
                case e: InvalidStateException => false
            }
        }
        
        checkpointWithRetries(0)
    }
}