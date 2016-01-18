### tuktu.dlib.generators.oaipmh.ListSetsGenerator
Retrieves the set structure of a repository.

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

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target repository.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Converts XML set descriptions to JSON?

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Flattens JSON set descriptions?

