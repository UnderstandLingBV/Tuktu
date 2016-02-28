### tuktu.ml.processors.association.ARMProcessor
Learns association rules on batched data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The name of the field that contains the transactions. This field must contain a Seq[Int].

    * **min_support** *(type: int)* `[Required]`
    - The minimum support of the frequent itemsets.

    * **min_confidence** *(type: double)* `[Required]`
    - The minimum confidence of the association rules to generate.

