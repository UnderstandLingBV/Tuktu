### tuktu.processors.FieldRenameProcessor
Renames / moves (nested) fields to the top under new field names, deleting its top-level ancestor.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be renamed / moved.
 
      * **[UNNAMED]** *(type: object)* `[Required]`

        * **path** *(type: array)* `[Required]`
        - The path at which the value is located.
 
          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - The new result name of the value at the end of the path (or the default).
 
