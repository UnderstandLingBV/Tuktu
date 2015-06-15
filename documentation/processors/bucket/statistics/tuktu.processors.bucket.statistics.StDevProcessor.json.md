### tuktu.processors.bucket.statistics.StDevProcessor
Individually computes the standard deviation of a list of fields containing numerical values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The standard deviation over these fields will be individually computed and returned under the respective field names as a single datum.

      * **[UNNAMED]** *(type: string)* `[Required]`

