### tuktu.nosql.generators.MongoDBAggregateGenerator
Executes tasks in an aggregation pipeline on a given list of nodes.

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

  * **config** *(type: object)* `[Required]`

    * **hosts** *(type: array)* `[Required]`
    - A list of node names, like node1.foo.com:27017. Port is optional, it is 27017 by default.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **database** *(type: string)* `[Required]`
    - The database name.

    * **collection** *(type: string)* `[Required]`
    - The name of the collection to aggregate.

    * **user** *(type: string)* `[Optional]`
    - The name of the user (if authentication is required).

    * **password** *(type: string)* `[Optional]`
    - The password of the user (if authentication is required).

    * **admin** *(type: boolean)* `[Required]`
    - Does authentication use the admin database?

    * **ScramSha1** *(type: boolean)* `[Required]`
    - Use the ScramSha1 authentication method (instead of CrAuth)?

    * **tasks** *(type: array)* `[Required]`
    - A list of aggregation tasks for the pipeline.

      * **[UNNAMED]** *(type: JsObject)* `[Required]`

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Are all results to be batched before pushing it on the channel.

