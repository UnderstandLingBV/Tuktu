### tuktu.nosql.generators.CassandraGenerator
Executes a query on a specified Cassandra node.

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

    * **host** *(type: string)* `[Required]`
    - The address of the node to connect to; optionally appended by :port, otherwise port 9042 will be assumed.

    * **type** *(type: string)* `[Optional]`
    - The execution type of the query.

    * **query** *(type: string)* `[Required]`
    - The query to be run.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Flatten the result, otherwise resultName -> result will be returned.

    * **fetch_size** *(type: int)* `[Optional, default = 100]`
    - The fetch size of the query.

