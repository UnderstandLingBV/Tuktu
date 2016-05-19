package tuktu.api

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI

import scala.io.Codec
import scala.io.Source

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.netaporter.uri.Uri
import java.net.URLDecoder
import java.io.FileInputStream
import java.io.InputStream

class S3CredentialProvider(id: String, key: String) extends AWSCredentials {
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
        else genericReader(Uri.parse(string).toURI)(codec)
    }
    
    /**
     * Generically reads a binary file
     */
    def genericBinaryReader(string: String): InputStream = {
        val uri = Uri.parse(string).toURI
        // Determine what to do
        uri.getScheme match {
            case "s3" => s3BinaryReader(string)
            case "file" | "" | null => fileBinaryReader(uri)
            case "hdfs" => hdfsBinaryReader(uri)
            case _ => throw new Exception("Unknown file format")
        }
    }

    /**
     * Reads from Local disk
     */
    def fileReader(uri: URI)(implicit codec: Codec): BufferedReader = {
        if (uri.toString.startsWith("//"))
            Source.fromFile(uri.getHost + File.separator + uri.getPath)(codec).bufferedReader
        else
            Source.fromFile(uri.getPath)(codec).bufferedReader
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
    private def parseS3Address(address: String) = {
        val split = address.drop(5).split("@")
        
        // Get credentials from address, must be URL encoded and before @
        val (id, key) = {
            val userInfo = split.head.split(":")
            (userInfo(0), URLDecoder.decode(userInfo(1), "utf-8"))
        }
        
        // Get the actual object
        val parts = split.drop(1).mkString("@").split("/")
        val bucketName = parts.head
        val keyName = parts.drop(1).mkString("/")
        
        (id, key, bucketName, keyName)
    }
    
    /**
     * Reads from S3
     */
    def s3Reader(address: String)(implicit codec: Codec): BufferedReader = {
        val (id, key, bucketName, keyName) = parseS3Address(address)
        
        // Set up S3 client
        val s3Client = new AmazonS3Client(new S3CredentialProvider(id, key))
        
        // Get the actual object
        val s3Object = s3Client.getObject(new GetObjectRequest(bucketName, keyName))

        // Return buffered reader
        new BufferedReader(new InputStreamReader(s3Object.getObjectContent, codec.decoder))
    }
    
    /**
     * Reads binary data from S3
     */
    def s3BinaryReader(address: String): InputStream = {
        val (id, key, bucketName, keyName) = parseS3Address(address)
        
        // Set up S3 client
        val s3Client = new AmazonS3Client(new S3CredentialProvider(id, key))
        
        // Get the actual object
        val s3Object = s3Client.getObject(new GetObjectRequest(bucketName, keyName))
        
        // Return the input stream
        s3Object.getObjectContent
    }
}