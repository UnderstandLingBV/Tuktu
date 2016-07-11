### tuktu.processors.FileRotatingStreamProcessor
Streams data into a file, rotates it based on a given scheme.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The file to be streamed into, it's important to add a formatter in the filename. For example "log-[yyyy-MM-dd-HH-mm].txt."

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The encoding used for writing.

    * **rotation_time** *(type: string)* `[Required]`
    - How much time in between rotations, e.g. "1 hour"

    * **field_separator** *(type: string)* `[Optional, default = ","]`
    - The field separator character.

    * **line_separator** *(type: string)* `[Optional]`
    - The line separator character, default is newline (\r\n).

    * **fields** *(type: array)* `[Required]`
    - The fields to be written.

      * **[UNNAMED]** *(type: string)* `[Required]`

