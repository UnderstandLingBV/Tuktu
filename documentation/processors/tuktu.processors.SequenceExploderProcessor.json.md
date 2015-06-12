### tuktu.processors.SequenceExploderProcessor
Takes a (JSON) sequence object and returns packets for each of the values in it overwriting the original sequence.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Optional]`
    - The field containing a sequence object.
 
    * **ignore_empty** *(type: boolean)* `[Optional]`
    - If set to true, will only continue with non-empty values, otherwise those will be filtered out.
 
