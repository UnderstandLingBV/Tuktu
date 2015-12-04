### tuktu.processors.FileReaderProcessor
Reads data from a file.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **filename** *(type: string)* `[Required]`
    - The file to be read.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The encoding used.

    * **start_line** *(type: int)* `[Optional, default = 0]`
    - The line to start reading from.

    * **line_separator** *(type: string)* `[Optional]`
    - The line separator character, default is newline (\r\n).

