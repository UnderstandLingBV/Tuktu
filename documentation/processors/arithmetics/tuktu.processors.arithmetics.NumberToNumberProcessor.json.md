### tuktu.processors.arithmetics.NumberToNumberProcessor
The numbers of one type into numbers of another type.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The name of the field to read the numbers from.

    * **target_type** *(type: string)* `[Required]`
    - The target number type (one of Long, Double, Float, BigDecimal or Int by default)

