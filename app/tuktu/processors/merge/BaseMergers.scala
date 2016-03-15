package tuktu.processors.merge

import tuktu.api.DataMerger
import tuktu.api.DataPacket
import tuktu.api.WebJsOrderedObject
import play.api.Play

/**
 * This merger iterates over all datapackets' data and merges them by taking
 * the union of all fields and overwrites existing ones
 */
class SimpleMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        new DataPacket(
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
        val jsField = Play.current.configuration.getString("tuktu.jsname").getOrElse("tuktu_js_field")
        
        new DataPacket({
                // Fold and zip the packets into one
                val x = packets.map(packet => packet.data)
                packets.map(packet => packet.data).fold(Nil)((x, y) => x.zipAll(y, Map.empty[String, Any], Map.empty[String, Any]).map(z => {
                    // Check if JS elements are there
                    if (z._2.contains(jsField) && z._1.contains(jsField)) {
                        // We must merge the content of these JS elements
                        val jsFirst = z._1(jsField).asInstanceOf[WebJsOrderedObject]
                        val jsSecond = z._2(jsField).asInstanceOf[WebJsOrderedObject]
                        (z._1 ++ z._2) + (jsField -> {
                            new WebJsOrderedObject({
                                jsFirst.items ++ jsSecond.items.map(elem => {
                                    // Make sure we don't get overlapping keys (from the original source packet for example)
                                    elem.filter(el => !jsFirst.items.flatMap(el => el).contains(el._1))
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
        new DataPacket({
                /**
                 * We must somehow deal with difference in length of the data packets
                 * 
                 * What we do here is check if one of the packets has size 1 and one of the others is lengthier. If so,
                 * we pad the 1-sized packet to the length of the other. If both have a size bigger than one but
                 * still not equal, we repeat the packet smallest in size
                 */
                val maxSize = packets.maxBy(pckt => pckt.data.size).data.size
                
                // Go over all packets, padding where required
                packets.map(packet => packet.data).fold(Nil)((x, y) => {
                    // Check size difference
                    if (y.size < maxSize) {
                        // Repeat the values of y
                        x.zipAll({
                            for (i <- 1 to maxSize) yield y(i % y.size)
                        }, Map(), Map()).map(z => z._1 ++ z._2)
                    }
                    else
                        // Similar to simple merger
                        x.zipAll(y, Map(), Map()).map(z => z._1 ++ z._2)
                })
        })
    }
}

/**
 * Gets the elements of all the DataPackets and just appends them into a new one
 */
class SerialMerger() extends DataMerger() {
    override def merge(packets: List[DataPacket]): DataPacket = {
        // Append all results
        new DataPacket(
                packets.map(elem => elem.data).flatten
        )
    }
}