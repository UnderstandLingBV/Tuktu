### tuktu.processors.bucket.concurrent.aggregate.CountProcessor
Counts the number of elements of a stream in a distributed fashion

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **nodes** *(type: array)* `[Required]`
    - The nodes to use for the SingleNode handler type.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field under which the result is returned.

