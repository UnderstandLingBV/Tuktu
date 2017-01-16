package tuktu.api

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URLDecoder

import scala.io.Codec
import scala.io.Source

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.netaporter.uri.Uri
import com.amazonaws.regions.Regions
import com.amazonaws.regions.Region

class TuktuAWSCredentialProvider(id: String, key: String) extends AWSCredentials {
    override def getAWSAccessKeyId() = id
    override def getAWSSecretKey() = key
}

object file {
    /**
     * A Generic Reader for multiple sources
     */
    def genericReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        uri.getScheme match {
            case "file" | "" | null => fileReader(uri)
            case "hdfs"             => hdfsReader(uri)
            case _                  => throw new Exception("Unknown file format")
        }
    }

    /**
     * Wrapper for a string instead of URI
     */
    def genericReader(string: String)(implicit codec: Codec): BufferedReader = {
        // Get URI
        val uri = Uri.parse(string).toURI
        if (uri.getScheme == "s3") s3Reader(string)(codec)
        else genericReader(uri)(codec)
    }

    /**
     * Generically reads a binary file
     */
    def genericBinaryReader(string: String): InputStream = {
        val uri = Uri.parse(string).toURI
        // Determine what to do
        uri.getScheme match {
            case "s3"               => s3BinaryReader(string)
            case "file" | "" | null => fileBinaryReader(uri)
            case "hdfs"             => hdfsBinaryReader(uri)
            case _                  => throw new Exception("Unknown file format")
        }
    }

    /**
     * Reads from Local disk
     */
    def fileReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        if (uri.toString.startsWith("//"))
            Source.fromFile(uri.getHost + File.separator + uri.getPath)(codec).bufferedReader
        else {
            val path = uri.getPath

            val cleanPath = if (path.startsWith("/"))
                path.substring(1)
            else
                path

            Source.fromFile(cleanPath)(codec).bufferedReader
        }
    }

    /**
     * Reads a binary file from local disk
     */
    def fileBinaryReader(uri: URI): InputStream = {
        if (uri.toString.startsWith("//"))
            new FileInputStream(new File(uri.getHost + File.separator + uri.getPath))
        else
            new FileInputStream(new File(uri.getPath))
    }

    /**
     * Reads from HDFS
     */
    def hdfsReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        val conf = new Configuration
        conf.set("fs.defaultFS", uri.getHost + ":" + uri.getPort)
        val fs = FileSystem.get(conf)
        val path = new Path(uri.getPath)
        new BufferedReader(new InputStreamReader(fs.open(path), codec.decoder))
    }

    /**
     * Reads binary data from HDFS
     */
    def hdfsBinaryReader(uri: URI): InputStream = {
        val conf = new Configuration
        conf.set("fs.defaultFS", uri.getHost + ":" + uri.getPort)
        val fs = FileSystem.get(conf)
        val path = new Path(uri.getPath)
        fs.open(path)
    }

    /**
     * Parses an S3 address to extract id, key, bucket and file name
     */
    def parseS3Address(address: String) = {
        // Remove s3://
        val url = address.drop(5)

        // Check if this address contains authentication credentials
        val (split, id, key) = {
            if (url.contains("@")) {
                // Get credentials from address, must be URL encoded and before @
                val userInfo = url.split("@").head.split(":")
                (url.split("@"), Some(userInfo(0)), Some(URLDecoder.decode(userInfo(1), "utf-8")))
            } else (Array("", url), None, None)
        }

        // Get the actual object
        val parts = split.drop(1).mkString("@").split("/")
        // Get the region, if set
        val region = if (List(
            "us-east-1", "us-west-2", "us-west-1", "eu-west-1", "eu-central-1",
            "ap-southeast-1", "ap-northeast-1", "ap-southeast-2", "ap-northeast-2",
            "sa-east-1").contains(parts.head)) {
            Some(parts.head)
        } else None
        val bucketName = region match {
            case Some(r) => parts.drop(1).head
            case None    => parts.head
        }
        val keyName =
            region match {
                case Some(r) => parts.drop(2).mkString("/")
                case None    => parts.drop(1).mkString("/")
            }

        (id, key, region, bucketName, keyName)
    }

    /**
     * Sets S3 region
     */
    def setS3Region(region: Option[String], client: AmazonS3Client) = region match {
        case Some("us-east-1")      => client.setRegion(Region.getRegion(Regions.US_EAST_1))
        case Some("us-west-2")      => client.setRegion(Region.getRegion(Regions.US_WEST_1))
        case Some("us-west-1")      => client.setRegion(Region.getRegion(Regions.US_WEST_2))
        case Some("eu-west-1")      => client.setRegion(Region.getRegion(Regions.EU_WEST_1))
        case Some("eu-central-1")   => client.setRegion(Region.getRegion(Regions.EU_CENTRAL_1))
        case Some("ap-southeast-1") => client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1))
        case Some("ap-northeast-1") => client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_1))
        case Some("ap-southeast-2") => client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_2))
        case Some("ap-northeast-2") => client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_2))
        case Some("sa-east-1")      => client.setRegion(Region.getRegion(Regions.SA_EAST_1))
        case _                      => client.setRegion(Region.getRegion(Regions.DEFAULT_REGION))
    }

    /**
     * Reads from S3
     */
    def s3Reader(address: String)(implicit codec: Codec): BufferedReader = {
        val (id, key, region, bucketName, keyName) = parseS3Address(address)

        // Set up S3 client
        val s3Client = (id, key) match {
            case (Some(i), Some(k)) => new AmazonS3Client(new TuktuAWSCredentialProvider(i, k))
            case _                  => new AmazonS3Client()
        }
        setS3Region(region, s3Client)

        // Get the actual object
        val s3Object = s3Client.getObject(new GetObjectRequest(bucketName, keyName))

        // Return buffered reader
        new BufferedReader(new InputStreamReader(s3Object.getObjectContent, codec.decoder))
    }

    /**
     * Reads binary data from S3
     */
    def s3BinaryReader(address: String): InputStream = {
        val (id, key, region, bucketName, keyName) = parseS3Address(address)

        // Set up S3 client
        val s3Client = (id, key) match {
            case (Some(i), Some(k)) => new AmazonS3Client(new TuktuAWSCredentialProvider(i, k))
            case _                  => new AmazonS3Client()
        }
        setS3Region(region, s3Client)

        // Get the actual object
        lazy val s3Object = s3Client.getObject(new GetObjectRequest(bucketName, keyName))

        // Return the input stream
        s3Object.getObjectContent
    }
}