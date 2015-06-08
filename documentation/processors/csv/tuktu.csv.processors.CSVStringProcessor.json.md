### tuktu.csv.processors.CSVStringProcessor
Turns the entire datapacket into a comma-separated (or other separators) string.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **separator** *(type: string)* `[Optional]`
    - The separator. Semicolon (;) by default.
 
    * **quote** *(type: string)* `[Optional]`
    - The quote character. Double quote (") by default.
 
    * **escape** *(type: string)* `[Optional]`
    - The escape character. Double backslash (\) by default.
 
