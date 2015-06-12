### tuktu.processors.StreamingDeduplicationProcessor
Deduplicates in a stream, meaning that only previously unseen data packets are forwarded.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The field combination to deduplicate for.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

