### tuktu.processors.BinaryFileStreamProcessor
Streams binary data into a file and closes it when it's done.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The file to be streamed into.

    * **field_bytes_separator** *(type: array)* `[Optional]`
    - A list of bytes (represented by ints) that optionalluy separate the fields per DataPacket. Default is no bytes in between fields.

      * **[UNNAMED]** *(type: int)* `[Required]`

    * **datum_bytes_separator** *(type: array)* `[Optional]`
    - Similarly to separating the content of fields inside a DataPacket, this list of bytes separates two distinct datums of a DataPacket. Default is no bytes in between datums.

      * **[UNNAMED]** *(type: int)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be written.

      * **[UNNAMED]** *(type: string)* `[Required]`

