### tuktu.processors.FieldRemoveProcessor
Removes specific top-level fields from each datum of the data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be removed.

      * **field** *(type: string)* `[Required]`
      - The field to be removed.

    * **ignore_empty_datums** *(type: boolean)* `[Optional, default = false]`
    - Ignore empty datums within each DataPacket.

    * **ignore_empty_datapackets** *(type: boolean)* `[Optional, default = false]`
    - Ignore empty DataPackets. DataPackets which consist of empty datums only are not ignored if ignore_empty_datums isn't true.

