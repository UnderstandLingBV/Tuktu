### tuktu.processors.ConvertToNumber
Converts an Any or Seq[Any] to a Number or a sequence of numbers, respectively, and stores them under the result name.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to be converted.

    * **locale** *(type: string)* `[Optional, default = "en"]`
    - The locale to be used to convert strings to numbers. For example: en, nl, ...

    * **number_type** *(type: string)* `[Optional, default = "double"]`
    - The type to which the field is to be converted, one of byte, short, int, long, float, double.

