### tuktu.web.generators.NewsAPIGenerator
Gets news from the News API.

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

    * **token** *(type: string)* `[Required]`
    - The access key/token given by News API.

    * **tags** *(type: array)* `[Optional]`
    - Keywords to look for in the title and/or description of the news articles.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **sources** *(type: array)* `[Optional]`
    - The news sources to keep track of. Tracks all sources if none are given

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **update_time** *(type: int)* `[Optional, default = 3600]`
    - How long to wait between each poll in seconds.

