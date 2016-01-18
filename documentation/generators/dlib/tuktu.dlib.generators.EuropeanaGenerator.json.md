### tuktu.dlib.generators.EuropeanaGenerator
Queries the Europeana API and returns pointers to the resulting records.

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

    * **query** *(type: string)* `[Required]`
    - A Europeana API query.

    * **apikey** *(type: string)* `[Required]`
    - A Europeana API key.

    * **maxresult** *(type: int)* `[Optional]`
    - The maximum number of results to be returned by each query.

