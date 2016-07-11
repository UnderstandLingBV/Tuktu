### tuktu.processors.FieldRemoveProcessor
Removes specific top-level fields from each datum of the data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **fields** *(type: array)* `[Required]`
    - The fields to be removed.

      * **field** *(type: string)* `[Required]`
      - The field to be removed.

