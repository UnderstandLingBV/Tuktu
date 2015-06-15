### tuktu.processors.bucket.statistics.MidrangeProcessor
Individually computes the midrange of a list of fields containing numerical values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The midrange over these fields will be individually computed and returned under the respective field names as a single datum.

      * **[UNNAMED]** *(type: string)* `[Required]`

