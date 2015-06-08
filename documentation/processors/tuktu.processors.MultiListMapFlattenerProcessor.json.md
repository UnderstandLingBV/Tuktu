### tuktu.processors.MultiListMapFlattenerProcessor
Takes a list of maps and flattens it by reading out a number of specific keys of that map. The resulting lists will be appended to the datapacket.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **list_field** *(type: string)* `[Required]`
    - The name of the field that contains the list of maps.
 
    * **map_fields** *(type: array)* `[Required]`
    - The name of the fields that need to be extracted from the separate maps.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **ignore_empty** *(type: boolean)* `[Optional]`
    - If set to true, will only continue with non-empty values
 
