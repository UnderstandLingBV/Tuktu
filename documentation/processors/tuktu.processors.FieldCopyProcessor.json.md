### tuktu.processors.FieldCopyProcessor
Copies a (nested) field to the top under a new result name.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be copied.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **path** *(type: array)* `[Required]`
        - The path at which the value is located. Leave empty to copy the whole datum.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - The result name the value will be copied to.

