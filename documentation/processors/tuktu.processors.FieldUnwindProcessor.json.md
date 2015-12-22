### tuktu.processors.FieldUnwindProcessor
Unwind the Seq field of a single data packet datum into separate data packets (one per element in the seq to unwind).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The name of the field to unwind.

