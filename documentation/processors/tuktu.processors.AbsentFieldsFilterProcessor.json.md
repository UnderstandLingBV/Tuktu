### tuktu.processors.AbsentFieldsFilterProcessor
Discards every datum that does not contain all of the required fields.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The required fields (when any of them is absent, the datum is discarded).

      * **[UNNAMED]** *(type: string)* `[Required]`

