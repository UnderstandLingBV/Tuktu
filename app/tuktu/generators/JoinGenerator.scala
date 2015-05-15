package tuktu.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.hashing.MurmurHash3

import akka.actor._
import akka.pattern.ask
import akka.remote.RemoteScope
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket

case class JoinPacket(
    data: Map[String, Any],
    sourceIndex: Int
)
case class JoinedPacket(
    data1: Map[String, Any],
    data2: Map[String, Any]
)

/**
 * Actors that does actual joining
 */
class JoinWorker(parent: ActorRef, keys: List[List[String]]) extends Actor with ActorLogging {
    // Mapping that keeps track of the joining
    var joinMap = collection.mutable.Map[Int, collection.mutable.Map[String, collection.mutable.ListBuffer[Map[String, Any]]]](
            0 -> collection.mutable.Map[String, collection.mutable.ListBuffer[Map[String, Any]]](),
            1 -> collection.mutable.Map[String, collection.mutable.ListBuffer[Map[String, Any]]]()
    )
    
    def receive() = {
        case jp: JoinPacket => {
            // Get the key value
            val key = (for (k <- keys(jp.sourceIndex)) yield jp.data(k)).mkString
            
            // Can we make a join?
            if (joinMap((jp.sourceIndex + 1) % 2).contains(key)) {
                // Yes, we can, send back
                val joinedRows = joinMap((jp.sourceIndex + 1) % 2)(key)
                
                joinedRows.foreach {row =>
                    // Send it back
                    parent ! (jp.sourceIndex match {
                        case 0 => new JoinedPacket(jp.data, row)
                        case 1 => new JoinedPacket(row, jp.data)
                    })
                }
            }
            
            // Always keep track of current item
            if (!joinMap(jp.sourceIndex).contains(key))
                joinMap(jp.sourceIndex) += key -> collection.mutable.ListBuffer[Map[String, Any]]()
            
            joinMap(jp.sourceIndex)(key) += jp.data
        }
    }
}

/**
 * This generator performs a join on 2 streams of data (2 other generators with processing pipelines)
 * in a distributed fashion, abstracting away from all possible details of joining
 */
class JoinGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    def packetToNodeHasher(packet: Map[String, Any], keys: List[String], maxSize: Int) = {
        val keyString = (for (key <- keys) yield packet(key)).mkString
        Math.abs(MurmurHash3.stringHash(keyString) % maxSize)
    }
    
    var joinActors = collection.mutable.ListBuffer[ActorRef]()
    var sourceActors = collection.mutable.Map[ActorRef, Int]()
    var sources = collection.mutable.ListBuffer[(String, List[String], String)]()
    
    override def receive() = {
        case config: JsValue => {
            // Get the list of nodes to perform the join on, must all be Tuktu nodes
            val nodeList = {
                val nodes = (config \ "nodes").asOpt[List[JsObject]]
                
                nodes match {
                    case None => List((
                            Play.current.configuration.getString("akka.remote.netty.tcp.hostname").getOrElse("127.0.0.1"),
                            Play.current.configuration.getInt("akka.remote.netty.tcp.port").getOrElse(2552)
                    ))
                    case Some(ns) => {
                        // Get host and portnumber
                        (for (n <- ns) yield ((n \ "host").as[String], (n \ "port").as[Int])).toList
                    } 
                }
            }
            
            // Get the original data streams to join
            val srcs = (config \ "sources").as[List[JsObject]].take(2)
            
            // Get config name and key for each
            srcs.foreach {src =>
                sources += {((src \ "name").as[String], (src \ "key").as[List[String]], (src \ "result").as[String])}
            }
                
            
            // We need to set up the actual generators that we will join later
            sources.zipWithIndex.foreach {el =>
                val source = el._1
                val index = el._2
                
                // We need to setup and start the two dataflows in a synchronous way
                val fut = sender ? new controllers.DispatchRequest(source._1, None, false, true, true, Some(self), 1)
                fut.onSuccess {
                    case ar: ActorRef => sourceActors += ar -> index
                }
            }
            
            // Set up the joiner nodes
            nodeList.foreach {node =>
                // Create join node remotely
                joinActors += Akka.system.actorOf(Props(classOf[JoinWorker], self, sources.map(src => src._2).toList)
                    .withDeploy(Deploy(scope = RemoteScope(Address("akka.tcp", "application", node._1, node._2)))))
            }
        }
        case sp: StopPacket => cleanup
        case dp: DataPacket => {
            // Here we need to hash the data packet based on key and forward it to the joiners
            dp.data.foreach {datum =>
                // See what source this came from
                val sourceIndex = sourceActors(sender)
                // Get bucket-hash from key
                val bucket = packetToNodeHasher(datum, sources(sourceIndex)._2, joinActors.size)
                
                // Send to the corresponding join actor
                joinActors(bucket) ! new JoinPacket(datum, sourceIndex)
            }
        }
        case jp: JoinedPacket => {
            // Send it on to the outside
            channel.push(new DataPacket(List(Map(
                    sources(0)._3 -> jp.data1,
                    sources(1)._3 -> jp.data2
            ))))
        }
    }
}