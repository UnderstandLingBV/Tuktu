### tuktu.processors.PacketFilterProcessor
Filters datums satisfying an expression.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **type** *(type: string)* `[Required]`
    - The type of the expression: 'groovy', 'negate' or 'simple'.

    * **expression** *(type: string)* `[Required]`
    - The expression to evaluate, can be arithmetics, boolean logic and simple predicates like. Supported predicates are: containsFields(field.path.name,...), which checks the presence of all given field paths -- isNumeric(field), which checks if the field is numeric -- isJSON(field.path.name,...), which checks if all given paths exists and are JSON values -- isNull(field), which checks if the field is null -- isEmpty(), which checks if the datum is empty (ie. contains no keys) -- isEmptyValue(field), which checks if a field contains an empty value (empty string, empty Seq, empty Map) -- listSize(field, operator, check), which checks if a field of type Seq[Any] matches check using the specified operator (supported are ==, !=, >=, <=, >, <).

    * **evaluate** *(type: boolean)* `[Optional, default = true]`
    - Evaluate the expression first. This is required when the (whole) expression (or parts of it) comes from the datum, e.g. when the expression is '${expression}' with expression -> ${field} == "Value" in the datum. This is not required when only basic values from the datum are used, e.g. ${field} == "Value" with field -> "Value" in the datum.

    * **constant** *(type: string)* `[Optional, default = "no"]`
    - Only relevant if evaluate above is true, otherwise the expression is obviously constant across all Datums and DataPackets. If evaluate above is true, this option expresses whether the expression is the same across all DataPackets and all Datums (enter 'global'), is the same for all Datums within each separate DataPacket (enter 'local'), or can differ even within DataPackets (enter 'no').

    * **default** *(type: string)* `[Optional]`
    - The default to be used if the evaluating the expression results in an error. Leave empty to throw an error and kill the flow, or use true or false.

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to include the entire DataPacket if one or more of the elements match the expression(s)

    * **batch_min_count** *(type: int)* `[Optional, default = 1]`
    - If batch is set to true, this number is the minimum amount of elements that should fulfill the expression(s)

