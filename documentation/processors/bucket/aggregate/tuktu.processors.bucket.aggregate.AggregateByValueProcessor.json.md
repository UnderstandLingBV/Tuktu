### tuktu.processors.bucket.aggregate.AggregateByValueProcessor
Aggregates values of a DataPacket by value.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **group** *(type: array)* `[Required]`
    - The list of fields which will be grouped together based on their distinct values across all specified fields. Leave empty to consider the whole DataPacket as a single group. The expression is calculated on each group separately, and the group fields with its values will be retained within each group's result, while everything else may not be unique within the group and hence will be dropped. If for example all monthly employee salaries were in a DataPacket (among other things), one could group by employee (some sort of ID), and then calculate the avg() of base ${salary} of each employee ({result -> average employee salary, employee -> employeeID}) instead of company-wide ({result -> average company-wide salary}).

      * **field** *(type: string)* `[Required]`

    * **base_value** *(type: string)* `[Required]`
    - An arithmetic expression to evaluate the base value. For example, for counting, use 1 - for summing, use the value of the field via ${field}.

    * **expression** *(type: string)* `[Required]`
    - The expression to compute. When calling pre-defined functions like sum(), avg(), median(), min(), max(), count(), do not enter a field name.

