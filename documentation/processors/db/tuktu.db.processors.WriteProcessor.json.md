### tuktu.db.processors.WriteProcessor
Writes elements in a DataPacket into buckets in the Tuktu DB

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **key** *(type: string)* `[Required]`
    - The key of the bucket to write to. Insert field values using ${..} notation.

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to wait for the writing to have occured before continuing.

