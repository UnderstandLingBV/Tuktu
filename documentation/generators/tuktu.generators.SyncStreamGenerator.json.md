### tuktu.generators.SyncStreamGenerator
Special sync generator that processes a tuple and returns the actual result.

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

    * **no_return** *(type: boolean)* `[Optional, default = false]`
    - Don't return data packets to the sender from which they were received.

    * **timeout** *(type: int)* `[Optional, default = 5]`
    - Timeout in seconds for the processor that returns data packets to the sender.

