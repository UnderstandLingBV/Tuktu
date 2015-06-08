### tuktu.csv.generators.flattening.XlsGenerator
No description present.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.
 
    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)
 
      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type
 
      * **instances** *(type: int)* `[Optional]`
      - The amount of instances per node of this handler type
 
  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **filename** *(type: string)* `[Required]`

    * **sheet_name** *(type: string)* `[Required]`

    * **value_name** *(type: string)* `[Required]`

    * **locators** *(type: array)* `[Required]`

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **name** *(type: string)* `[Required]`

        * **type** *(type: string)* `[Required]`

        * **params** *(type: JsObject)* `[Required]`

    * **flattened** *(type: boolean)* `[Optional]`

    * **data_start_col** *(type: int)* `[Required]`

    * **data_end_col** *(type: int)* `[Required]`

    * **end_field** *(type: object)* `[Required]`

      * **column** *(type: int)* `[Required]`

      * **value** *(type: string)* `[Required]`

