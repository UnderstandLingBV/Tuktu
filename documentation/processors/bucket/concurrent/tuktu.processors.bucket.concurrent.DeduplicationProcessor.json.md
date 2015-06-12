### tuktu.processors.bucket.concurrent.DeduplicationProcessor
Deduplicates elements in a bucketed data packet in a distributed fashion.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **nodes** *(type: array)* `[Required]`
    - The (Tuktu) nodes to run the sorting on.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to deduplicate elements with same entries from.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

