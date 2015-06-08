### tuktu.csv.processors.CSVReaderProcessor
Reads out values as CSV from a field.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Optional]`
    - The field the CSV string resides in.
 
    * **headers** *(type: array)* `[Optional]`
    - The headers of the CSV string. These headers will form the names of the columns.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **headers_from_first** *(type: boolean)* `[Optional]`
    - If set to true, the first data packet will be used to extract headers from.
 
    * **remove_original** *(type: boolean)* `[Optional]`
    - Whehter to remove the original field.
 
    * **separator** *(type: string)* `[Optional]`
    - The separator. Semicolon (;) by default.
 
    * **quote** *(type: string)* `[Optional]`
    - The quote character. Double quote (") by default.
 
    * **escape** *(type: string)* `[Optional]`
    - The escape character. Double backslash (\) by default.
 
