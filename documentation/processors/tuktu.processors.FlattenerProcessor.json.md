### tuktu.processors.FlattenerProcessor
Recursively flattens a map object, appending the keys to the previous keys separated by a given separator.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be flattened.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **separator** *(type: string)* `[Required]`
    - The separator inserted between two iterations.
 
