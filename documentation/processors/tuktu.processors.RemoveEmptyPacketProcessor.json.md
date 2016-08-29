### tuktu.processors.RemoveEmptyPacketProcessor
Removes data packets from the stream that contain no data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **remove_empty_datums** *(type: boolean)* `[Optional, default = false]`
    - Remove empty datums first, and then empty DataPackets? Or only empty DataPackets?

