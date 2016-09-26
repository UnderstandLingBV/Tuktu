### tuktu.csv.processors.FixedWidthProcessor
Turns a fixed-width file into separate fields bases on the fixed widths.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **headers** *(type: array)* `[Optional]`
    - The headers of the fields.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - Name of the header.

    * **field** *(type: string)* `[Required]`
    - The field containing the fixed-width delimited string.

    * **widths** *(type: array)* `[Required]`
    - The widths of all the fields.

      * **[UNNAMED]** *(type: int)* `[Required]`
      - Width of the field.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to add the resulting fields as top-level citizen (true) or in the resultname field (false).

