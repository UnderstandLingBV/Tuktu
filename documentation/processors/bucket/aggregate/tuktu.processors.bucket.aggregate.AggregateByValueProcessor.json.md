### tuktu.processors.bucket.aggregate.AggregateByValueProcessor
Aggregates values of a DataPacket by value.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to aggregate the expression on.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **field** *(type: string)* `[Required]`
        - The actual field to aggregate on. Use dot-notation to traverse paths.

        * **base_value** *(type: string)* `[Required]`
        - An arithmetic expression to evaluate the base value. For example, for counting, use 1 - for summing, use the value of the field via ${field}.

    * **expression** *(type: string)* `[Required]`
    - The expression to compute. When calling pre-defined functions like sum(), do not enter a field name.

