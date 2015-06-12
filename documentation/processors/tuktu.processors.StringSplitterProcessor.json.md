### tuktu.processors.StringSplitterProcessor
Splits a string up into a list of values based on a separator.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The name of the field that should be split.

    * **separator** *(type: string)* `[Required]`
    - The separator used to split on.

    * **overwrite** *(type: boolean)* `[Optional, default = false]`
    - Whether the original value in field should be overwritten (true) or whether the result should be appended as a separate field resultName (false).

