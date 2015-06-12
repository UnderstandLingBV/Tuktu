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

  * **result** *(type: string)* `[Optional]`

  * **config** *(type: object)* `[Required]`

    * **nodes** *(type: array)* `[Optional]`
    - The list of nodes to perform the join on, must all be Tuktu nodes.

      * **[UNNAMED]** *(type: object)* `[Optional]`

        * **host** *(type: string)* `[Required]`
        - Default is the Play configuration value of akka.remote.netty.tcp.hostname, or 127.0.0.1 if that is not set.

        * **port** *(type: int)* `[Required]`
        - Default is the Play configuration value of akka.remote.netty.tcp.port, or 2552 if that is not set.

    * **sources** *(type: array)* `[Required]`
    - The data streams to join.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **name** *(type: string)* `[Required]`

        * **key** *(type: array)* `[Required]`
        - The keys to join on.

          * **[UNNAMED]** *(type: string)* `[Required]`

        * **result** *(type: string)* `[Required]`
        - Key whose value determines the resultName, for both streams respectively.

