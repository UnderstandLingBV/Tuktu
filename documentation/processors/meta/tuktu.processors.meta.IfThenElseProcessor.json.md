### tuktu.processors.meta.IfThenElseProcessor
A processor that evaluates a Tuktu predicate for each datum and applies a subflow to all datums satisfying the predicate and another to all datums that do not satisfy the predicate.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Optional, default = "UNNAMED"]`
    - The name to show in the monitor for the if- and else-branches.

    * **expression** *(type: string)* `[Required]`
    - The expression to evaluate, can be arithmetics, boolean logic and simple predicates like. Supported predicates are: containsFields(field.path.name,...), which checks the presence of all given field paths -- isNumeric(field), which checks if the field is numeric -- isJSON(field.path.name,...), which checks if all given paths exists and are JSON values -- isNull(field), which checks if the field is null -- isEmpty(), which checks if the datum is empty (ie. contains no keys) -- isEmptyValue(field), which checks if a field contains an empty value (empty string, empty Seq, empty Map) -- listSize(field, operator, check), which checks if a field of type Seq[Any] matches check using the specified operator (supported are ==, !=, >=, <=, >, <).

    * **evaluate** *(type: boolean)* `[Optional, default = true]`
    - Evaluate the expression first. This is required when the (whole) expression (or parts of it) comes from the datum, e.g. when the expression is '${expression}' with expression -> ${field} == "Value" in the datum. This is not required when only basic values from the datum are used, e.g. ${field} == "Value" with field -> "Value" in the datum.

    * **constant** *(type: string)* `[Optional, default = "no"]`
    - Only relevant if evaluate above is true, otherwise the expression is obviously constant across all Datums and DataPackets. If evaluate above is true, this option expresses whether the expression is the same across all DataPackets and all Datums (enter 'global'), is the same for all Datums within each separate DataPacket (enter 'local'), or can differ even within DataPackets (enter 'no').

    * **default** *(type: string)* `[Optional]`
    - The default to be used if the evaluating the expression results in an error. Leave empty to throw an error and kill the flow, or use true or false.

    * **then_pipeline** *(type: object)* `[Required]`
    - The then-pipeline. This points to a config file and start processor to execute on all datums satisfying the predicate.

      * **config** *(type: string)* `[Required]`
      - The name of the config file that contains the subflow for all datums satisfying the predicate.

      * **start** *(type: string)* `[Required]`
      - The name of the start processor for all datums satisfying the predicate.

    * **else_pipeline** *(type: object)* `[Required]`
    - The else-pipeline. This points to a config file and start processor to execute on all datums NOT satisfying the predicate.

      * **config** *(type: string)* `[Required]`
      - The name of the config file that contains the subflow for all datums NOT satisfying the predicate.

      * **start** *(type: string)* `[Required]`
      - The name of the start processor for all datums NOT satisfying the predicate.

