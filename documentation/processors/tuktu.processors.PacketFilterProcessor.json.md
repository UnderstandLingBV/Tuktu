### tuktu.processors.PacketFilterProcessor
Filters data packets satisfying a number of expressions.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **expressions** *(type: array)* `[Required]`
    - The list of expressions.

      * **expression** *(type: object)* `[Required]`
      - The actual expression.

        * **type** *(type: string)* `[Required]`
        - The type of the expression: 'groovy', 'negate' or 'simple'.

        * **and_or** *(type: string)* `[Optional, default = "and"]`
        - In case of normal or negate type, do all statements need to evaluate to true (and), or at least one (or).

        * **expression** *(type: any)* `[Required]`
        - The expression itself. This can be a string that needs to be evaluated, or it can be a nested array of new expressions that follow the same structure as any top-level expression.

