### tuktu.ml.processors.association.FPGrowthProcessor
Runs the FP Growth algorithm on batched data to obtain frequent itemsets.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The name of the field that contains the transactions. This field must contain a Seq[Int].

    * **min_support** *(type: int)* `[Required]`
    - The minimum support of the frequent itemsets.

