### tuktu.processors.DataPacketWrapperProcessor
Wraps either the whole list of Datums of a DataPacket under a new result name as a whole, or each datum under a new result name separately.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **as_whole** *(type: boolean)* `[Optional, default = true]`
    - If true, wraps the whole list of datums into a single datum under result: result -> List(datums). Otherwise, each datum gets separately wrapped under result: result -> datum.

