### tuktu.processors.JsObjectImploderProcessor
Implodes an array of JSON object-fields into an array of JSON strings found at a specified sub-path, which is then joined by a given separator, overwriting its top-level ancestor.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to implode.
 
      * **[UNNAMED]** *(type: object)* `[Required]`

        * **path** *(type: array)* `[Required]`
        - The path to the JSON array of JSON objects. The result will overwrite its top-level ancestor.
 
          * **[UNNAMED]** *(type: string)* `[Required]`

        * **subpath** *(type: array)* `[Required]`
        - The sub-path to a JSON string within each JSON object.
 
          * **[UNNAMED]** *(type: string)* `[Required]`

        * **separator** *(type: string)* `[Required]`
        - The separator character used between two such JSON strings.
 
