### tuktu.processors.SizeBufferProcessor
Buffers data packets until a specific amount has been reached, then releases the buffer

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **size** *(type: int)* `[Required]`
    - The amount of data packets to buffer before continuing.

