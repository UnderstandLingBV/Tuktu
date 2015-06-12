### tuktu.processors.JsonFetcherProcessor
Fetches a single field to put it as top-level citizen of the data. Can traverse top-level JSON objects.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to fetch.
 
      * **[UNNAMED]** *(type: object)* `[Required]`

        * **default** *(type: any)* `[Optional]`
        - The default value to be used if the path cannot be traversed until the end.
 
        * **path** *(type: array)* `[Required]`
        - The path at which the value is located.
 
          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - The new result name of the value at the end of the path (or the default).
 
