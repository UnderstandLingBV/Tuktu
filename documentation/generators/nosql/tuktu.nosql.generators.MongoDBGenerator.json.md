### tuktu.nosql.generators.MongoDBGenerator
Executes a query on a given list of nodes.

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
    - The name of the collection to open.

    * **user** *(type: string)* `[Optional]`
    - The name of the user (if authentication is required).

    * **password** *(type: string)* `[Optional]`
    - The password of the user (if authentication is required).

    * **admin** *(type: boolean)* `[Required]`
    - Does authentication use the admin database?

    * **ScramSha1** *(type: boolean)* `[Required]`
    - Use the ScramSha1 authentication method (instead of CrAuth)?

    * **query** *(type: JsObject)* `[Required]`
    - Find the documents matching these given criteria.

    * **filter** *(type: JsObject)* `[Optional]`
    - Filter results by this projection.

    * **sort** *(type: JsObject)* `[Optional]`
    - Sort results by this projection.

    * **limit** *(type: int)* `[Optional]`
    - Limit results by this number.

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Are all results to be batched before pushing it on the channel.

    * **batch_size** *(type: int)* `[Optional, default = 50]`
    - The size of the batches to get from mongo.

