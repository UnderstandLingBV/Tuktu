### tuktu.processors.bucket.statistics.MedianProcessor
Individually computes the median of a list of fields containing numerical values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The median over these fields will be individually computed and returned under the respective field names as a single datum.

      * **[UNNAMED]** *(type: string)* `[Required]`

