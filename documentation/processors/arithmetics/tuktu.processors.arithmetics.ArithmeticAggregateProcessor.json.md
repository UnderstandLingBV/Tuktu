### tuktu.processors.arithmetics.ArithmeticAggregateProcessor
Calculates the result of the given formula, allows for aggregation over an entire DataPacket.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **calculate** *(type: string)* `[Required]`
    - The formula which is calculated, can use aggregation functions avg(field), sum(field), median(field), stdev(field), min(field), max(field) and count(field).

    * **do_rounding** *(type: boolean)* `[Optional, default = false]`
    - Round the result

    * **number_of_decimals** *(type: int)* `[Optional, default = 0]`
    - How many figures to round to.

