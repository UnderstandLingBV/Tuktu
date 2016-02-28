### tuktu.ml.processors.preprocessing.MissingvaluesProcessor
Replaces missing values with target values.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Optional]`
    - The fields to replace missing values for. Applies to all fields if not specified.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - Field name.

    * **replacements** *(type: array)* `[Required]`
    - The target values per data type for missing values.

      * **[UNNAMED]** *(type: object)* `[Required]`
      - 

        * **type** *(type: string)* `[Required]`
        - The data type (ie. int, long, double, string etc. - use any as special case).

        * **target** *(type: any)* `[Required]`
        - The target value, enclose strings in double quotes!

