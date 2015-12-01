package tuktu.nosql.processors.mongodb

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee

import play.api.libs.json._
import play.api.libs.json.Json._
import play.modules.reactivemongo.json._

import play.api.Play.current

import reactivemongo.api.MongoDriver
import reactivemongo.api.commands.AggregationFramework

//import reactivemongo.api.commands.Command
import reactivemongo.api.DefaultDB
import reactivemongo.api.MongoDriver
import reactivemongo.api._
import play.modules.reactivemongo.json.JSONSerializationPack
import reactivemongo.api.commands.Command

import scala.collection.Set

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

import tuktu.api._
import tuktu.nosql.util._

/**
 * Provides a helper to run specified database commands (as long as the command result is less than 16MB in size).
 */

class MongoDBRawCommandProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var command: JsObject = _
    var db: DefaultDB = _
    var resultOnly: Boolean = _
    var timeout: Int = _

    override def initialize(config: JsObject) 
    {
        // Prepare db connection
        val dbHosts = (config \ "hosts").as[List[String]]
        val dbName = (config \ "database").as[String]
        val driver = new MongoDriver
        val connection = driver.connection( dbHosts )
        db = connection( dbName )

        // Get command
        command = (config \ "command").as[JsObject]
        
        // Get result format
        resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(false)
        
        // Get the timeout of the service
        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            
            val runner = Command.run( JSONSerializationPack )
            val futureResult = runner.apply( db, runner.rawCommand( command ) ).one[JsObject]
            val result = Await.result( futureResult, timeout.seconds )
            if (resultOnly)
            {
                datum + (resultName -> (result \ "result") )
            }
            else
            {
                datum + (resultName -> result)
            }
        })
    })
     
}