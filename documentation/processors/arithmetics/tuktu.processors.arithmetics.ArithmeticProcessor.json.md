### tuktu.processors.arithmetics.ArithmeticProcessor
Calculates the result of the given formula.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **calculate** *(type: string)* `[Required]`
    - The formula which is calculated.

    * **do_rounding** *(type: boolean)* `[Optional]`
    - Round the result

    * **number_of_decimals** *(type: int)* `[Optional, default = 0]`
    - How many figures to round to.

