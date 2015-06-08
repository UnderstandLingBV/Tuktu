### tuktu.csv.generators.BatchedCsvGenerator
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

    * **has_headers** *(type: boolean)* `[Optional]`

    * **predef_headers** *(type: array)* `[Optional]`

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **flattened** *(type: boolean)* `[Optional]`

    * **separator** *(type: string)* `[Optional]`

    * **quote** *(type: string)* `[Optional]`

    * **escape** *(type: string)* `[Optional]`

    * **batch_size** *(type: int)* `[Optional]`

