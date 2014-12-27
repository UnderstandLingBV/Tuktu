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
                packets.map(packet => packet.data).fold(Nil)((x, y) => x.zipAll(y, Map(), Map()).map(x => x._1 ++ x._2))
        )
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