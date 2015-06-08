### tuktu.social.generators.TwitterSearchGenerator
No description present.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.
 
    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)
 
      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type
 
      * **instances** *(type: int)* `[Optional]`
      - The amount of instances per node of this handler type
 
  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **credentials** *(type: object)* `[Required]`

      * **consumer_key** *(type: string)* `[Required]`

      * **consumer_secret** *(type: string)* `[Required]`

      * **access_token** *(type: string)* `[Required]`

      * **access_token_secret** *(type: string)* `[Required]`

    * **filters** *(type: object)* `[Required]`

      * **keywords** *(type: array)* `[Required]`

        * **[UNNAMED]** *(type: string)* `[Required]`

