### tuktu.nosql.processors.sql.RecursiveLookupProcessor
No description present.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`

    * **user** *(type: string)* `[Required]`

    * **password** *(type: string)* `[Required]`

    * **driver** *(type: string)* `[Required]`

    * **columns** *(type: array)* `[Required]`

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **name** *(type: string)* `[Required]`

        * **var** *(type: string)* `[Required]`

    * **from** *(type: string)* `[Required]`

    * **where** *(type: string)* `[Required]`

    * **include_original** *(type: boolean)* `[Optional]`

    * **n** *(type: int)* `[Optional]`

