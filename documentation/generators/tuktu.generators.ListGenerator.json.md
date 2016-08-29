### tuktu.generators.ListGenerator
Generates a list of given values.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **values** *(type: array)* `[Required]`
    - List of values to be streamed.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **separate** *(type: boolean)* `[Optional, default = true]`
    - Send each item separately, or as one big list.

    * **type** *(type: string)* `[Optional, default = "String"]`
    - What the type of the message is. Can be one of JsObject, JsArray, String, Boolean, Double, Int

