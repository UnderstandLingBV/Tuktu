### tuktu.dlib.generators.LREGenerator
Queries the Learning Resource Exchange (LRE) API and returns the corresponding list of LRE resource identifiers.

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

    * **service** *(type: string)* `[Required]`
    - The LRE API endpoint.

    * **query** *(type: string)* `[Required]`
    - A CNF query.

    * **limit** *(type: int)* `[Optional]`
    - The maximum number of results to be returned by each query.

    * **resultOnly** *(type: boolean)* `[Required]`
    - Only returns record identifiers?

