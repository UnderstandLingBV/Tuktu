### tuktu.social.processors.PinterestTaggerProcessor
Gets the board that a pin was made on.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **object_field** *(type: string)* `[Required]`
    - The name of the field in which the Pinterest object is.

    * **tags** *(type: object)* `[Required]`

      * **boards** *(type: array)* `[Optional]`
      - The boards to tag for, must be following the structure of <username>/<boardname>.

        * **[UNNAMED]** *(type: string)* `[Required]`

    * **exclude_on_none** *(type: boolean)* `[Required]`
    - Exclude pins which do not match any of the boards.

