### tuktu.processors.MapFlattenerProcessor
Takes a Map[String, Any] and makes it a top-level citizen, overwriting other top-level fields with the same name.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field which contains the map.
 
