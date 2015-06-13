### tuktu.processors.bucket.statistics.CovarianceProcessor
Computes the covariance matrix of a list of fields containing numerical values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The covariance matrix over these fields will be computed and returned under result as a single datum.

      * **[UNNAMED]** *(type: string)* `[Required]`

