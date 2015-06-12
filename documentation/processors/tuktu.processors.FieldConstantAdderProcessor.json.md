### tuktu.processors.FieldConstantAdderProcessor
Adds a field with a constant (static) value to the data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **value** *(type: string)* `[Required]`
    - A Tuktu String to evaluate and add.

    * **is_numeric** *(type: boolean)* `[Optional, default = false]`
    - Convert Tuktu String to Long, or not.

