### tuktu.processors.HeadOfListProcessor
Gets the head element of a list within a DataPacket.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the list.

    * **keep_original_field** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the original field is kept if no head of the list is found. Set to false will remove the original field if fetching the head of the list fails.

