### tuktu.processors.FieldFilterProcessor
Filters specific fields from the data packet, and saves them under a new top-level field name.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be filtered.
 
      * **[UNNAMED]** *(type: object)* `[Required]`

        * **default** *(type: any)* `[Optional]`
        - The value to be used if the path cannot be traversed until the end.
 
        * **path** *(type: array)* `[Required]`
        - The path at which the value is located.
 
          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - The new result name of the value at the end of the path (or the default).
 
