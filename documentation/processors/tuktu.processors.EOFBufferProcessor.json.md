### tuktu.processors.EOFBufferProcessor
Buffers until EOF (end of data stream) is found.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not the result of the remaining flow is required to be send back.

