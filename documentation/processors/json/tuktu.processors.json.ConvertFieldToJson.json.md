### tuktu.processors.json.ConvertFieldToJson
Converts a field to JSON.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the value to be converted to JSON.

    * **append** *(type: boolean)* `[Optional, default = false]`
    - Append the data as resultName or overwrite field.

