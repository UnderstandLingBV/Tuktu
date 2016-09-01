package tuktu.processors.merge

import tuktu.api.DataMerger
import tuktu.api.DataPacket
import tuktu.api.WebJsOrderedObject
import play.api.Play
import play.api.cache.Cache
import play.api.Play.current

/**
 * This merger iterates over all datapackets' data and merges them by taking
 * the union of all fields and overwrites existing ones
 */
class SimpleMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        DataPacket(
                // Fold and zip the packets into one
                packets.map(packet => packet.data).fold(Nil)((x, y) => x.zipAll(y, Map(), Map()).map(z => z._1 ++ z._2))
        )
    }
}

/**
 * Same as simple merger, but has awareness of JS elements in the child-packets which need their content to be merged
 */
class JSMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        val jsField = Cache.getAs[String]("web.jsname").getOrElse(Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field"))

        DataPacket({
                // Fold and zip the packets into one
                val x = packets.map(packet => packet.data)
                packets.map(packet => packet.data).fold(Nil)((x, y) => x.zipAll(y, Map.empty[String, Any], Map.empty[String, Any]).map(z => {
                    // Check if JS elements are there
                    if (z._2.contains(jsField) && z._1.contains(jsField)) {
                        // We must merge the content of these JS elements
                        val jsFirst = z._1(jsField).asInstanceOf[WebJsOrderedObject]
                        val jsSecond = z._2(jsField).asInstanceOf[WebJsOrderedObject]
                        // keep track of which keys are duplicate and remove them
                        val removalKeys = jsFirst.items.flatMap(el => el.keySet)

                        (z._1 ++ z._2) + (jsField -> {
                            new WebJsOrderedObject({
                                jsFirst.items ++ jsSecond.items.map(elem => {
                                    // Make sure we don't get overlapping keys (from the original source packet for example)
                                    elem -- removalKeys
                                })
                            })
                        })
                    }
                    else z._1 ++ z._2
                }))
        })
    }
}

/**
 * Works the same as the SimpleMerger, but pads data (potentially repeatedly) if sizes are different
 */
class PaddingMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        if (packets.isEmpty || packets.exists { packet => packet.isEmpty })
            DataPacket(Nil)
        else
            DataPacket({
                /**
                 * We must somehow deal with difference in length of the data packets
                 *
                 * What we do here is check if one of the packets has size 1 and one of the others is lengthier. If so,
                 * we pad the 1-sized packet to the length of the other. If both have a size bigger than one but
                 * still not equal, we repeat the packet smallest in size
                 */
                val maxSize = packets.maxBy { packet => packet.size }.size

                // Go over all packets, padding where required
                packets
                    .filter { packet => packet.nonEmpty }
                    .foldLeft(for (i <- 1 to maxSize) yield Map[String, Any]()) { (accum, packet) =>
                        accum
                            .zip(for (i <- 1 to maxSize) yield packet.data(i % packet.size))
                            .map { case (accumMap, packetMap) => accumMap ++ packetMap }
                    }.toList
            })
    }
}

/**
 * Gets the elements of all the DataPackets and just appends them into a new one
 */
class SerialMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        // Append all results
        DataPacket(
                packets.map(elem => elem.data).flatten
        )
    }
}