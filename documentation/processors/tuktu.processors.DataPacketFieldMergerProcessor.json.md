### tuktu.processors.DataPacketFieldMergerProcessor
Merges the specified fields into one data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **value** *(type: string)* `[Required]`
    - A Tuktu String to evaluate and add.

    * **is_numeric** *(type: boolean)* `[Optional, default = false]`
    - Convert Tuktu String to Long, or not.

    * **is_decimal** *(type: boolean)* `[Optional, default = false]`
    - Convert Tuktu String to Double, or not.

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - If set to true, works on the entire DP, otherwise it works on a datum a time.

    * **fields** *(type: array)* `[Required]`
    - The fields to be filtered.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **default** *(type: any)* `[Optional]`
        - The value to be used if the path cannot be traversed until the end.

        * **path** *(type: array)* `[Required]`
        - The path at which the value is located.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - The new result name of the value at the end of the path (or the default).

