package tuktu.processors.merge

import tuktu.api.DataMerger
import tuktu.api.DataPacket

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