### tuktu.processors.TupleListStringImploder
Takes a tuple or a list of tuples and concatenates the values of the tuple(s) into a string with a specified separator.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The arrays to be imploded.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **path** *(type: array)* `[Required]`
        - The path to the tuple field. The result will overwrite its top-level ancestor.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **separator** *(type: string)* `[Required]`
        - The separator character used between elements.

