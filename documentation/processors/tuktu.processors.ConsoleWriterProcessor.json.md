### tuktu.processors.ConsoleWriterProcessor
Prints out data packets to console.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **prettify** *(type: boolean)* `[Optional, default = false]`
    - Try to prettify the result written to console.

    * **fields** *(type: array)* `[Optional]`
    - The fields to be printed. Leave empty to print everything.

      * **field** *(type: string)* `[Required]`
      - The field to be printed.

