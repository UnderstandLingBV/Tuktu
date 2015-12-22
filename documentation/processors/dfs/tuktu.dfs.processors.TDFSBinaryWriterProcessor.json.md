### tuktu.dfs.processors.TDFSBinaryWriterProcessor
Writes a binary file to the Tuktu DFS.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The file to be streamed into.

    * **block_size** *(type: int)* `[Optional, default = 64]`
    - The block size in MB.

    * **field** *(type: string)* `[Required]`
    - The field to write.

