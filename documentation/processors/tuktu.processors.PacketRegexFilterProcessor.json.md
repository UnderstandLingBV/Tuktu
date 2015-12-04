### tuktu.processors.PacketRegexFilterProcessor
Filters data packets satisfying a number of regular expressions.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **expressions** *(type: array)* `[Required]`
    - The list of expressions.

      * **expression** *(type: object)* `[Required]`
      - The actual expression.

        * **type** *(type: string)* `[Required]`
        - The type of the expression: 'negate' or 'simple'.

        * **and_or** *(type: string)* `[Optional, default = "and"]`
        - In case of a list of expressions below, do all statements need to evaluate to true (and), or at least one (or).

        * **field** *(type: string)* `[Required]`
        - The field to be tested for the regular expression. If type is not negate, all datums are selected which have at least one match, otherwise all datums are selected which have no matches.

        * **expression** *(type: any)* `[Required]`
        - The regular expression itself. This can be a string that needs to be evaluated, or it can be a nested array of new expressions that follow the same structure as any top-level expression.

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to include the entire DataPacket if one or more of the elements match the expression(s).

    * **batch_min_count** *(type: int)* `[Optional, default = 1]`
    - If batch is set to true, this number is the minimum amount of elements that should fulfill the expression(s) before including the whole DataPacket.

