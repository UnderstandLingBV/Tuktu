### tuktu.processors.ContainsAllFilterProcessor
Given a field with a list of maps, a field with a list of values, and a field, check for all possible field -> value pairs whether there is at least one map in the list which contains it, or drop that data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to check for the values.
 
    * **contains_field** *(type: string)* `[Required]`
    - The field which contains an array of the values to check for.
 
    * **field_list** *(type: string)* `[Required]`
    - A field which contains a list of maps.
 
