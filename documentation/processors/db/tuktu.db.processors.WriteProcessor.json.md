### tuktu.db.processors.WriteProcessor
Writes elements in a DataPacket into buckets in the Tuktu DB

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **keys** *(type: array)* `[Required]`
    - The fields that need to be read out from the DataPacket to construct the bucket to write to.

      * **[UNNAMED]** *(type: string)* `[Optional]`

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to wait for the writing to have occured before continuing.

