### tuktu.processors.PacketFilterProcessor
Filters data packets satisfying a number of expressions.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **type** *(type: string)* `[Required]`
    - The type of the expression: 'groovy', 'negate' or 'simple'.

    * **expression** *(type: string)* `[Required]`
    - The expression to evaluate, can be arithmetics, boolean logic and simple predicates like. Supported predicates are: containsFields(field.path.name,...), which checks the presence of all given field paths -- isNumeric(field), which checks if the field is numeric -- isJSON(field.path.name,...), which checks if all given paths exists and are JSON values -- isNull(field), which checks if the field is null -- isEmpty(), which checks if the datum is empty (ie. contains no keys) -- isEmptyValue(field), which checks if a field contains an empty value (empty string, empty Seq, empty Map).

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to include the entire DataPacket if one or more of the elements match the expression(s)

    * **batch_min_count** *(type: int)* `[Optional, default = 1]`
    - If batch is set to true, this number is the minimum amount of elements that should fulfill the expression(s)

