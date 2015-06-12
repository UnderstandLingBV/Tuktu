### tuktu.processors.json.ConvertFromJson
Takes a JSON field and converts it into something usable.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the JSON to be converted into a Map.

    * **overwrite** *(type: boolean)* `[Optional, default = false]`
    - Overwrite field, or append the data as resultName.

