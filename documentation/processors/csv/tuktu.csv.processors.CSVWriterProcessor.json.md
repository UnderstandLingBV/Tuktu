### tuktu.csv.processors.CSVWriterProcessor
No description present.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The filename to write to.
 
    * **encoding** *(type: string)* `[Optional]`
    - The character encoding, UTF-8 by default.
 
    * **fields** *(type: array)* `[Optional]`
    - A selection of the fields to write out. If not set, all fields are written.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **separator** *(type: string)* `[Optional]`
    - The separator. Semicolon (;) by default.
 
    * **quote** *(type: string)* `[Optional]`
    - The quote character. Double quote (") by default.
 
    * **escape** *(type: string)* `[Optional]`
    - The escape character. Double backslash (\) by default.
 
