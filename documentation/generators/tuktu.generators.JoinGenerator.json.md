### tuktu.generators.JoinGenerator
Performs a join on two streams of data (two other generators with processing pipelines) in a distributed fashion.

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

    * **nodes** *(type: array)* `[Optional]`
    - The list of nodes to perform the join on. If nothing is entered, all nodes are used.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - The address (hostname or IP) of the node.

    * **sources** *(type: array)* `[Required]`
    - The data streams to join.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **name** *(type: string)* `[Required]`

        * **key** *(type: array)* `[Required]`
        - The keys to join on.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - Key whose value determines the resultName, for both streams respectively.

