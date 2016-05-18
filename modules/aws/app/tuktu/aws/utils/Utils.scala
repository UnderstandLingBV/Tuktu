package tuktu.aws.utils

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.regions.Regions
import com.amazonaws.regions.Region

object Utils {
    def setS3Region(region: Option[String], s3Client: AmazonS3Client) = region match {
        case Some("ap-northeast-1") => s3Client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_1))
        case Some("ap-northeast-2") => s3Client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_2))
        case Some("ap-southeast-1") => s3Client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1))
        case Some("ap-southeast-2") => s3Client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_2))
        case Some("cn-north-1") => s3Client.setRegion(Region.getRegion(Regions.CN_NORTH_1))
        case Some("eu-central-1") => s3Client.setRegion(Region.getRegion(Regions.EU_CENTRAL_1))
        case Some("eu-west-1") => s3Client.setRegion(Region.getRegion(Regions.EU_WEST_1))
        case Some("sa-east-1") => s3Client.setRegion(Region.getRegion(Regions.SA_EAST_1))
        case Some("us-east-1") => s3Client.setRegion(Region.getRegion(Regions.US_EAST_1))
        case Some("us-west-1") => s3Client.setRegion(Region.getRegion(Regions.US_WEST_1))
        case Some("us-west-2") => s3Client.setRegion(Region.getRegion(Regions.US_WEST_2))
        case _ => s3Client.setRegion(Region.getRegion(Regions.DEFAULT_REGION))
    }
}