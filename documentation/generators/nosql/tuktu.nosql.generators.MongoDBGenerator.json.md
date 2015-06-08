### tuktu.nosql.generators.MongoDBGenerator
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
 
  * **result** *(type: string)* `[Optional]`

  * **config** *(type: object)* `[Required]`

    * **hosts** *(type: array)* `[Required]`

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **database** *(type: string)* `[Required]`

    * **collection** *(type: string)* `[Required]`

    * **query** *(type: JsObject)* `[Required]`

    * **batch** *(type: boolean)* `[Optional]`

