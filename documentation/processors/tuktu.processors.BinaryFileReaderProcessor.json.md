### tuktu.processors.BinaryFileReaderProcessor
Reads a binary file.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **filename** *(type: string)* `[Required]`
    - The file to be read.

    * **chunk_size** *(type: int)* `[Optional, default = 8092]`
    - The chunk size to use.

