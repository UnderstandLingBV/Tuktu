### tuktu.dlib.generators.LearningRegistryGenerator
Harvests records from a Learning Registry node.

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

    * **node** *(type: string)* `[Required]`
    - The Learning Registry node to harvest from.

    * **from** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a lower bound for datestamp-based selective harvesting.

    * **until** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a upper bound for datestamp-based selective harvesting.

