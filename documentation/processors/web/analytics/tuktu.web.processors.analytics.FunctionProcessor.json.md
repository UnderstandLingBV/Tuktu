### tuktu.web.processors.analytics.FunctionProcessor
Adds a JS function definition.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the JS function.

    * **params** *(type: array)* `[Optional]`
    - The parameters of the JS function.

      * **param** *(type: string)* `[Required]`
      - Parameter name.

    * **body** *(type: string)* `[Required]`
    - The body/code of the JS function.

