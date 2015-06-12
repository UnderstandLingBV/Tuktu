### tuktu.csv.processors.CSVWriterProcessor
Writes CSV with headers to a file.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The filename to write to.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The character encoding.

    * **fields** *(type: array)* `[Optional]`
    - A selection of the fields to write out. If not set, all fields are written.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **separator** *(type: string)* `[Optional, default = ";"]`
    - The separator character.

    * **quote** *(type: string)* `[Optional, default = "\""]`
    - The quote character.

    * **escape** *(type: string)* `[Optional, default = "\\"]`
    - The escape character.

