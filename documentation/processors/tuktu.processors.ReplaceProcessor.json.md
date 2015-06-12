### tuktu.processors.ReplaceProcessor
Replaces one top-level citizen's String representation for another String.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to replace.
 
    * **sources** *(type: array)* `[Required]`
    - Replace all occurrences (case-sensitive) of this list of Strings, with the respective entries of targets.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **targets** *(type: array)* `[Required]`
    - Replace all occurrences (case-sensitive) of sources, with the respective entries of this list of Strings.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

