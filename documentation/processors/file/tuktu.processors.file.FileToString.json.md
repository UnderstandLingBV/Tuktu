### tuktu.processors.file.FileToString
Parses a file to String.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_field** *(type: string)* `[Required]`
    - The field that contains the file to be parsed.

    * **charset** *(type: string)* `[Optional, default = "utf-8"]`
    - Charset that will be used to decode the file.

    * **drop** *(type: int)* `[Optional, default = 0]`
    - Drop first initial lines.

    * **drop_right** *(type: int)* `[Optional, default = 0]`
    - Drop last lines

    * **overwrite** *(type: boolean)* `[Optional, default = false]`
    - Overwrite file field with the parsed JSON.

