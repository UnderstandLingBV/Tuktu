### tuktu.processors.KeyImploderProcessor
Implodes all elements in a DataPacket into a single element of a DataPacket based on key

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be imploded.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **merge** *(type: boolean)* `[Optional, default = false]`
    - If the imploded fields should be merged into the first datum of the DP, or if everything else should be discarded.

