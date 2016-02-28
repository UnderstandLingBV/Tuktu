### tuktu.dlib.processors.MapMergerProcessor
Merges two JSON objects into a third one.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **map1** *(type: string)* `[Required]`
    - The field that contains the first map to merge.

    * **map2** *(type: string)* `[Required]`
    - The field that contains the second map to merge.

    * **priority** *(type: string)* `[Optional]`
    - The map (i.e., map1 or map2) that serves as a basis for the merging.

