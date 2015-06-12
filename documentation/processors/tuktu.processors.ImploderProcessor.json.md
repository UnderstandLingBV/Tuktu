### tuktu.processors.ImploderProcessor
Implodes arrays into a string, overwriting its top-level ancestor.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The arrays to be imploded.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **path** *(type: array)* `[Required]`
        - The path to the array. The result will overwrite its top-level ancestor.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **separator** *(type: string)* `[Required]`
        - The separator character used between elements.

