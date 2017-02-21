package tuktu.db.actors

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

import org.apache.commons.collections4.map.PassiveExpiringMap

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api._
import scala.concurrent.Await
import org.nustaq.serialization.FSTObjectOutput

// helper case class to get Overview from each node separately
case class InternalOverview(
    offset: Int)
case class InternalContent(
    cr: ContentRequest)
case class DBIdentityRequest()
case class DBActorIdentity(
    node: String,
    id: ActorRef)
case class IntiateDaemonUpdate()

/**
 * Daemon for Tuktu's DB operations
 */
class DBDaemon(tuktudb: TrieMap[String, collection.mutable.ListBuffer[Map[String, Any]]]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // Get this local node
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    // Get cluster nodes
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())

    val dataDir = new File(Play.current.configuration.getString("tuktu.db.data").getOrElse("db/data"))

    // Check the persist strategy
    val persistType = Play.current.configuration.getString("tuktu.db.persiststrategy.type").getOrElse("time")

    /**
     * Hashes a list of keys and a data packet to specific set of nodes
     */
    private def hash(keys: List[Any]) =
        utils.indexToNodeHasher(
            keys,
            Cache.getAs[Int]("tuktu.db.replication"),
            true)

    /**
     * Builds the location of the DBDaemon on a specified node and corresponding ClusterNode
     */
    def location(node: String): String = location((node, clusterNodes(node)))
    def location(node: (String, ClusterNode)): String = {
        if (node._1 == homeAddress)
            "/user/tuktu.db.Daemon"
        else
            "akka.tcp://application@" + node._1 + ":" + node._2.akkaPort + "/user/tuktu.db.Daemon"
    }

    def receive = {
        case ip: InitPacket => {}
        case sr: StoreRequest => {
            // We need to store original sender because we will make more requests (potentially to ourselves) before returning a result
            val originalSender: ActorRef = sender

            val elementsPerNode: Map[String, List[DBObject]] =
                (for {
                    element <- sr.elements

                    // Hash it
                    nodes = hash(List(element.key))

                    node <- nodes
                } yield {
                    (node, element)
                }).groupBy(_._1).mapValues(_.map(_._2))

            // Send replicate request to all
            if (sr.needReply) {
                val futs = for ((node, elements) <- elementsPerNode) yield {
                    Akka.system.actorSelection(location(node)) ? new ReplicateRequest(elements, true)
                }
                Future.sequence(futs).map { _ => originalSender ! "ok" }
            } else {
                for ((node, elements) <- elementsPerNode) {
                    Akka.system.actorSelection(location(node)) ! new ReplicateRequest(elements, false)
                }
            }
        }
        case rr: ReplicateRequest => {
            // Add the data packet to our in-memory store
            for (elem <- rr.elements) {
                tuktudb.get(elem.key) match {
                    case None    => tuktudb += elem.key -> collection.mutable.ListBuffer[Map[String, Any]](elem.value)
                    case Some(l) => l += elem.value
                }
            }

            if (rr.needReply) sender ! "ok"

            // If we persist based on number of updates (size) or if we persist on each update, do it now
            if (persistType == "update")
                self ! new PersistRequest
        }
        case rr: ReadRequest => {
            // We need to store original sender because we will make more requests (potentially to ourselves) before returning a result
            val originalSender: ActorRef = sender

            // Probe first
            if (tuktudb.contains(rr.key))
                sender ! new ReadResponse(tuktudb(rr.key) toList)
            else if (rr.isFirst) {
                // We need to query all other nodes
                val nodes = clusterNodes.keys.toList diff List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))

                // There are no other nodes
                if (nodes.isEmpty) originalSender ! new ReadResponse(List())
                else {
                    // Request all other daemons for the data
                    val requests = for (node <- clusterNodes) yield {
                        (Akka.system.actorSelection(location(node)) ? new ReadRequest(rr.key, false)).asInstanceOf[Future[ReadResponse]]
                    }

                    // Get all the responses
                    Future.sequence(requests).map { responses =>
                        // Find the first response
                        responses.find(r => !r.value.isEmpty) match {
                            case None =>
                                // No one had data, tough luck
                                originalSender ! new ReadResponse(List())
                            case Some(r) => originalSender ! r
                        }
                    }
                }
            } else
                // We failed to find the bucket
                originalSender ! new ReadResponse(List())
        }
        case dr: DeleteRequest => {
            // We need to store original sender because we will make more requests (potentially to ourselves) before returning a result
            val originalSender: ActorRef = sender

            // Need reply or not?
            if (dr.needReply) {
                val futs = for (node <- clusterNodes) yield {
                    Akka.system.actorSelection(location(node)) ? new DeleteActionRequest(dr.key, true)
                }
                Future.sequence(futs).map(_ => originalSender ! "ok")
            } else {
                for (node <- clusterNodes) {
                    Akka.system.actorSelection(location(node)) ! new DeleteActionRequest(dr.key, false)
                }
            }
        }
        case dar: DeleteActionRequest => {
            tuktudb -= dar.key
            if (dar.needReply) sender ! "ok"
        }
        case pp: PersistRequest => Future {
            // Persist to disk
            val foo = new FSTObjectOutput(new FileOutputStream(dataDir + File.separator + "db.data"))
            foo.writeObject(tuktudb, classOf[TrieMap[String, collection.mutable.ListBuffer[Map[String, Any]]]])
            foo.close
        }
        case or: OverviewRequest => {
            // We need to store original sender because we will make more requests (potentially to ourselves) before returning a result
            val originalSender: ActorRef = sender

            val requests = for (node <- clusterNodes) yield {
                (Akka.system.actorSelection(location(node)) ? new InternalOverview(or.offset)).asInstanceOf[Future[OverviewReply]]
            }

            Future.fold(requests)(Map.empty[String, Int])(_ ++ _.bucketCounts).map {
                result => originalSender ! new OverviewReply(result)
            }
        }
        case or: InternalOverview => {
            if (or.offset == -1)
                sender ! new OverviewReply(
                    tuktudb.keys.map(name => name -> 0).toMap)
            else
                sender ! new OverviewReply(
                    tuktudb.drop(or.offset * 50).take(50).map(bucket => (bucket._1, bucket._2.size)).toMap)
        }
        case cr: ContentRequest => {
            // We need to store original sender because we will make more requests (potentially to ourselves) before returning a result
            val originalSender: ActorRef = sender

            val requests = for (node <- clusterNodes) yield {
                (Akka.system.actorSelection(location(node)) ? new InternalContent(cr)).asInstanceOf[Future[ContentReply]]
            }

            Future.fold(requests)(List.empty[Map[String, Any]])(_ ++ _.data).map {
                result => originalSender ! new ContentReply(result)

            }
        }
        case ic: InternalContent => {
            val cr = ic.cr
            sender ! new ContentReply({
                // Do we even have the key or not?
                tuktudb.get(cr.key).map { _.drop(cr.offset * 10).take(10).toList }.getOrElse(Nil)
            })
        }
    }
}