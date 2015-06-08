### tuktu.nosql.generators.CassandraGenerator
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

    * **host** *(type: string)* `[Required]`

    * **type** *(type: string)* `[Optional]`

    * **query** *(type: string)* `[Required]`

    * **flatten** *(type: boolean)* `[Optional]`

    * **fetch_size** *(type: int)* `[Optional]`

