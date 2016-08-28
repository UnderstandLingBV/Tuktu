package tuktu.db.actors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.Identify
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.ClusterNode
import tuktu.api.DataPacket
import tuktu.api.DeleteRequest
import tuktu.api.InitPacket
import tuktu.api.PersistRequest
import tuktu.api.ReadRequest
import tuktu.api.ReadResponse
import tuktu.api.ReplicateRequest
import tuktu.api.StoreRequest
import tuktu.api.utils
import scala.util.Random
import tuktu.api.DeleteActionRequest
import tuktu.api.OverviewRequest
import tuktu.api.OverviewReply
import java.io.File
import java.nio.file.Files
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import tuktu.api.ContentRequest
import tuktu.api.ContentReply

// helper case class to get Overview from each node separately
case class InternalOverview(
    offset: Int
)
case class InternalContent(
        cr: ContentRequest
)

/**
 * Daemon for Tuktu's DB operations
 */
class DBDaemon() extends Actor with ActorLogging {    
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Local in-memory database
    private val tuktudb = collection.mutable.Map[String, collection.mutable.ListBuffer[Map[String, Any]]]()
    
    // Get this local node
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    // Get cluster nodes
    val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
    
    // Create data directory
    val dataDir = new File(Play.current.configuration.getString("tuktu.db.data").getOrElse("db/data"))
    if (!dataDir.exists)
        dataDir.mkdirs
        
    // Check the persist strategy
    val (persistType, persistValue) = (
        Play.current.configuration.getString("tuktu.db.persiststrategy.type").getOrElse("time"),
        Play.current.configuration.getInt("tuktu.db.persiststrategy.value").getOrElse(1)
    )
    // Persist only when a time limit has collapsed
    if (persistType == "time") {
        Akka.system.scheduler.schedule(
                persistValue seconds,
                persistValue seconds,
                self,
                new PersistRequest
        )
    }
    // For size persistence
    var persistUpdates = 0
    
    // Get all daemons
    val dbDaemons = {
        // Get all other daemons
        val otherNodes = clusterNodes.keys.toList diff List(Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
        
        val futures = for (hostname <- otherNodes) yield {
            val location = "akka.tcp://application@" + hostname  + ":" + clusterNodes(hostname).akkaPort + "/user/tuktu.db.Daemon"
            // Get the identity
            (Akka.system.actorSelection(location) ? Identify(None)).asInstanceOf[Future[ActorIdentity]]
        }
        ((homeAddress, self)::otherNodes.zip(Await.result(Future.sequence(futures), timeout.duration).map(id => id.getRef))).toMap
    }
    
    /**
     * Hashes a list of keys and a data packet to specific set of nodes
     */
    private def hash(keys: List[Any]) =
        utils.indexToNodeHasher(
                keys,
                Cache.getAs[Int]("tuktu.db.replication"),
                true
        )
    
    def receive() = {
        case ip: InitPacket => {}
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
                val futs = for (elemWithNode <- elementsPerNode) yield dbDaemons(elemWithNode._1) ? new ReplicateRequest(elemWithNode._2.map(_._2), true)
                Future.sequence(futs).map(_ => sender ! "ok")
            }
            else {
                elementsPerNode.foreach(elemWithNode => {
                    dbDaemons(elemWithNode._1) ! new ReplicateRequest(
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
                    tuktudb += elem.key -> collection.mutable.ListBuffer[Map[String, Any]]()
                tuktudb(elem.key) += elem.value
            })
            
            if (rr.needReply) sender ! "ok"
            
            // Increase persistence counter
            if (persistType == "size") persistUpdates += 1
            // If we persist based on number of updates (size) or if we persist on each update, do it now
            if (persistUpdates >= persistValue || persistValue == "update") {
                self ! new PersistRequest
                persistUpdates = 0
            }
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
                    val fut = dbDaemons(Random.shuffle(nodes).head) ? rr
                    
                    fut.map {
                        case rr: ReadResponse => sender ! rr
                    }
                }
            }
        }
        case dr: DeleteRequest => {
            // Remove the data packet
            val nodes = hash(List(dr.key))
            
            // Need reply or not?
            if (dr.needReply) {
                val futs = for (node <- nodes) yield dbDaemons(node) ? new DeleteActionRequest(dr.key, true)
                Future.sequence(futs).map(_ => sender ! "ok")
            } else nodes.foreach(node => dbDaemons(node) ! new DeleteActionRequest(dr.key, false))
        }
        case dar: DeleteActionRequest => {
            tuktudb -= dar.key
            if (dar.needReply) sender ! "ok"
        }
        case pp: PersistRequest => {
            // Persist to disk
            val oos = new ObjectOutputStream(new FileOutputStream(dataDir + File.separator + "db.data"))
            oos.writeObject(tuktudb)
            oos.close
        }
        case or: OverviewRequest => {
            // need to store original sender
            val originalSender = sender
            
            val requests = dbDaemons.map(_._2 ? new InternalOverview(or.offset)).asInstanceOf[Seq[Future[OverviewReply]]]

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
            
            val requests = dbDaemons.map(_._2 ? new InternalContent(cr)).asInstanceOf[Seq[Future[ContentReply]]]

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