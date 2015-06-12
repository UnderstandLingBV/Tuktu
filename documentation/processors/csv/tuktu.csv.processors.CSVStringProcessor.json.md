### tuktu.csv.processors.CSVStringProcessor
Turns the entire datapacket into a comma-separated (or other separators) string.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **separator** *(type: string)* `[Optional, default = ";"]`
    - The separator character.

    * **quote** *(type: string)* `[Optional, default = "\""]`
    - The quote character.

    * **escape** *(type: string)* `[Optional, default = "\\"]`
    - The escape character.

