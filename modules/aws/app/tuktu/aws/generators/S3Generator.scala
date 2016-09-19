package tuktu.aws.generators

import tuktu.api.BaseGenerator
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import tuktu.api.InitPacket
import tuktu.api.DecreasePressurePacket
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import play.api.libs.json.JsValue
import tuktu.api.BackPressurePacket
import tuktu.api.file
import com.amazonaws.services.s3.AmazonS3Client
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current
import java.io.File
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.S3ObjectSummary
import tuktu.api.TuktuAWSCredentialProvider

case class ListFilePacket(
        bucketName: String,
        prefix: String,
        isInitial: Boolean
)

/**
 * Helper actor  that reads files from a bucket, possibly recursive
 */
class ListFilesActor(s3Client: AmazonS3Client, recursive: Boolean, parent: ActorRef) extends Actor with ActorLogging {
    def receive() = {
        case lfp: ListFilePacket => {
            // Get all the files
            var objects = s3Client.listObjects(new ListObjectsRequest()
                .withBucketName(lfp.bucketName)
                .withPrefix(lfp.prefix)
            )
            
            // Get first batch
            objects.getObjectSummaries.toArray.toList.asInstanceOf[List[S3ObjectSummary]].foreach(summary => {
                if (summary.getKey.endsWith("/") && recursive) self ! new ListFilePacket(lfp.bucketName, summary.getKey, false)
                else parent ! summary.getKey
            })
            // Keep going
            while (objects.isTruncated) {
                objects = s3Client.listNextBatchOfObjects(objects)
                
                objects.getObjectSummaries.toArray.toList.asInstanceOf[List[S3ObjectSummary]].foreach(summary => {
                    if (summary.getKey.endsWith("/") && recursive) self ! new ListFilePacket(lfp.bucketName, summary.getKey, false)
                    else parent ! summary.getKey
                })
            }
            
            if (lfp.isInitial) self ! new StopPacket
        }
        case sp: StopPacket => parent ! new StopPacket
    }
}

/**
 * Generator that lists the file names/keys of all files present in an S3 bucket, potentially recursive
 */
class S3BucketListerGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def _receive = {
        case config: JsValue => {
            // Recursive or not?
            val recursive = (config \ "recursive").asOpt[Boolean].getOrElse(false)
            
            // Get S3 stuff
            val (id, key, region, bucket, fileName) = file.parseS3Address((config \ "file_name").as[String])
            // Create S3 client
            val s3Client = (id, key) match {
                case (Some(i), Some(k)) => new AmazonS3Client(new TuktuAWSCredentialProvider(i, k))
                case _ => new AmazonS3Client()
            }
            file.setS3Region(region, s3Client)
            
            // Set up actor to enlist files
            val lister = Akka.system.actorOf(Props(classOf[ListFilesActor], s3Client, recursive, self))
                    
            // List files in current one
            lister ! new ListFilePacket(bucket, fileName, true)
        }
        case name: String => channel.push(DataPacket(List(Map(resultName -> name))))
    }
}