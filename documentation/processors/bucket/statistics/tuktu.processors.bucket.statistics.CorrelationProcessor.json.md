### tuktu.processors.bucket.statistics.CorrelationProcessor
Computes the correlation matrix of a list of fields containing numerical values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The correlation matrix over these fields will be computed and returned under result as a single datum.

      * **[UNNAMED]** *(type: string)* `[Required]`

