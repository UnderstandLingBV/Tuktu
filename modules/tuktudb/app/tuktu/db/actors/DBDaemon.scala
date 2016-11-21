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
import scala.collection.mutable.Queue
import scala.concurrent.Await

// helper case class to get Overview from each node separately
case class InternalOverview(
    offset: Int
)
case class InternalContent(
        cr: ContentRequest
)
case class DBIdentityRequest()
case class DBActorIdentity(
        node: String,
        id: ActorRef
)
case class IntiateDaemonUpdate()

/**
 * Daemon for Tuktu's DB operations
 */
class DBDaemon(tuktudb: TrieMap[String, Queue[Map[String, Any]]]) extends Actor with ActorLogging {    
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Get this local node
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    // Get cluster nodes
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
    
    val dataDir = new File(Play.current.configuration.getString("tuktu.db.data").getOrElse("db/data"))
    
    // Map to get all the other daemons, use 4 * timeout so the periodic check is supposedly faster
    val dbDaemons = new PassiveExpiringMap[String, ActorRef](4 * timeout.duration.toMillis)
    // Always add ourselves, so TuktuDB will always function
    dbDaemons.put(homeAddress, self)
    
    // Check the persist strategy
    val persistType = Play.current.configuration.getString("tuktu.db.persiststrategy.type").getOrElse("time")
    
    /**
     * Hashes a list of keys and a data packet to specific set of nodes
     */
    private def hash(keys: List[Any]) =
        utils.indexToNodeHasher(
                keys,
                Cache.getAs[Int]("tuktu.db.replication"),
                true
        )
        
    // Periodically schedule checking of DB daemons
    Akka.system.scheduler.schedule(
            2 * timeout.duration,
            2 * timeout.duration,
            self,
            new IntiateDaemonUpdate
    )
    
    /**
     * Helper function to get the other DB daemons
     */
    def getOtherDaemons() {
        // Update self
        dbDaemons.put(homeAddress, context.parent)
        
        // Request all other daemons for their IDs
        val otherNodes = clusterNodes.keys.toList diff List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
        otherNodes.foreach(hostname => {
            val location = "akka.tcp://application@" + hostname + ":" + clusterNodes(hostname).akkaPort + "/user/tuktu.db.Daemon"
            Akka.system.actorSelection(location) ! new DBIdentityRequest()
        })
    }
    
    def receive() = {
        case initiate: IntiateDaemonUpdate => getOtherDaemons
        case idr: DBIdentityRequest => {
            // Another DB daemon asked us to send it our address
            sender ! new DBActorIdentity(homeAddress, context.parent)
        }
        case id: DBActorIdentity => {
            // Another DB daemon is letting us know its presence
            dbDaemons.put(id.node, id.id)
        }
        case ip: InitPacket => getOtherDaemons
        case sr: StoreRequest => {
            val elementsPerNode = ({
                for {
                    element <- sr.elements
                    
                    // Hash it
                    nodes = hash(List(element.key))
                    
                    node <- nodes
                } yield {
                    (node, element)
                }
            }).groupBy(_._1)
            
            // Send replicate request to all
            if (sr.needReply) {
                val futs = for (elemWithNode <- elementsPerNode) yield dbDaemons.get(elemWithNode._1) ? new ReplicateRequest(elemWithNode._2.map(_._2), true)
                Future.sequence(futs).map(_ => sender ! "ok")
            }
            else {
                elementsPerNode.foreach(elemWithNode => {
                    dbDaemons.get(elemWithNode._1) ! new ReplicateRequest(
                            elemWithNode._2.map(_._2),
                            false
                    )
                })
            }
        }
        case rr: ReplicateRequest => {
            // Add the data packet to our in-memory store
            rr.elements.foreach(elem => {
                if (!tuktudb.contains(elem.key))
                    tuktudb += elem.key -> Queue[Map[String, Any]]()
                tuktudb(elem.key) += elem.value
            })
            
            if (rr.needReply) sender ! "ok"
            
            // If we persist based on number of updates (size) or if we persist on each update, do it now
            if (persistType == "update")
                self ! new PersistRequest
        }
        case rr: ReadRequest => {
            // Probe first
            if (tuktudb.contains(rr.key)) sender ! new ReadResponse(tuktudb(rr.key) toList)
            else {
                // We need to query other nodes
                val nodes = hash(List(rr.key)) diff List(homeAddress)
                
                // There are no other nodes
                if (nodes.isEmpty) sender ! new ReadResponse(List())
                else {
                    // One must have it, pick any
                    val fut = (dbDaemons.get(Random.shuffle(nodes).head) ? rr).map {
                        case rr: ReadResponse => sender ! rr
                    }
                    Await.ready(fut, timeout.duration)
                }
            }
        }
        case dr: DeleteRequest => {
            // Remove the data packet
            val nodes = hash(List(dr.key))
            
            // Need reply or not?
            if (dr.needReply) {
                val futs = for (node <- nodes) yield dbDaemons.get(node) ? new DeleteActionRequest(dr.key, true)
                Future.sequence(futs).map(_ => sender ! "ok")
            } else nodes.foreach(node => dbDaemons.get(node) ! new DeleteActionRequest(dr.key, false))
        }
        case dar: DeleteActionRequest => {
            tuktudb -= dar.key
            if (dar.needReply) sender ! "ok"
        }
        case pp: PersistRequest => Future {
            // Persist to disk
            val oos = new ObjectOutputStream(new FileOutputStream(dataDir + File.separator + "db.data"))
            oos.writeObject(tuktudb.map(l => l._1 -> l._2.toList).asInstanceOf[TrieMap[String, List[Map[String, Any]]]])
            oos.close
        }
        case or: OverviewRequest => {
            // need to store original sender
            val originalSender = sender
            
            val requests = dbDaemons.values.toArray(Array[ActorRef]()).toSeq
                .map(_ ? new InternalOverview(or.offset)).asInstanceOf[Seq[Future[OverviewReply]]]

            Future.fold(requests)(Map.empty[String, Int])(_ ++ _.bucketCounts).map {
                result => originalSender ! new OverviewReply(result) 
            }
        }
        case or: InternalOverview => {
            sender ! new OverviewReply(
                    tuktudb.drop(or.offset * 50).take(50).map(bucket => (bucket._1, bucket._2.size)).toMap
            )
        }
        case cr: ContentRequest => {
            // Need to store original sender
            val originalSender = sender
            
            val requests = dbDaemons.values.toArray(Array[ActorRef]()).toSeq
                .map(_ ? new InternalContent(cr)).asInstanceOf[Seq[Future[ContentReply]]]

            Future.fold(requests)(List.empty[Map[String, Any]])(_ ++ _.data).map {
                result => originalSender ! new ContentReply(result) 
            }
        }
        case ic: InternalContent => {
            val cr = ic.cr
            sender ! new ContentReply({
                    // Do we even have the key or not?
                    if (tuktudb.contains(cr.key)) {
                        tuktudb(cr.key).drop(cr.offset * 10).take(10).toList
                    } else List()
            })
        }
    }
}