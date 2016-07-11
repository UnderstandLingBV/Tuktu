### tuktu.processors.file.FileToJson
Parses a file to JSON.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_field** *(type: string)* `[Required]`
    - The field that contains the file to be parsed.

    * **charset** *(type: string)* `[Optional, default = "utf-8"]`
    - Charset that will be used to decode the file.

