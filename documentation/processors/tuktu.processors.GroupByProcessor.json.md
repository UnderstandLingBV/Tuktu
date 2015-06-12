### tuktu.processors.GroupByBuffer
Buffers and groups data by the value list of the provided fields.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to group on. First field will be used as root-grouping, then the next field etc.

      * **[UNNAMED]** *(type: string)* `[Required]`

