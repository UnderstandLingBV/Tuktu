### tuktu.processors.file.FileToJson
Parses a file to JSON.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_field** *(type: string)* `[Required]`
    - The field that contains the file to be parsed.

    * **overwrite** *(type: boolean)* `[Optional, default = false]`
    - Overwrite file field with the parsed JSON.

    * **charset** *(type: string)* `[Optional, default = "utf-8"]`
    - Charset that will be used to decode the file.

