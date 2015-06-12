### tuktu.processors.FileStreamProcessor
Streams data into a file and closes it when it's done.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The file to be streamed into.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The encoding used for writing.

    * **field_separator** *(type: string)* `[Optional, default = ","]`
    - The field separator character.

    * **line_separator** *(type: string)* `[Optional]`
    - The line separator character, default is newline (\r\n).

    * **fields** *(type: array)* `[Required]`
    - The fields to be written.

      * **[UNNAMED]** *(type: string)* `[Required]`

