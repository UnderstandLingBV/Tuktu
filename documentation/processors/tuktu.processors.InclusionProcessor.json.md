### tuktu.processors.InclusionProcessor
Includes or excludes specific data packets which do not match a given expression.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **expression** *(type: string)* `[Required]`
    - A groovy expression if type is groovy, or a comma-separated list of field=value statements.
 
    * **type** *(type: string)* `[Required]`
    - The type of the expression: 'normal', 'negate', or 'groovy'.
 
    * **and_or** *(type: string)* `[Optional]`
    - In case of normal or negate type, do all statements need to evaluate to true (and), or at least one (or).
 
