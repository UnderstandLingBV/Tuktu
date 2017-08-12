### tuktu.processors.TimeBufferProcessor
Buffers data packets for a specific amount of time.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **interval** *(type: int)* `[Required]`
    - The amount of milliseconds to buffer for.

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not the result of the remaining flow is required to be send back.

