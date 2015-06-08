### tuktu.social.generators.FacebookGenerator
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

      * **access_token** *(type: string)* `[Required]`

    * **filters** *(type: object)* `[Required]`

      * **keywords** *(type: array)* `[Optional]`

        * **[UNNAMED]** *(type: string)* `[Required]`

      * **users** *(type: array)* `[Optional]`

        * **[UNNAMED]** *(type: string)* `[Required]`

    * **update_time** *(type: long)* `[Optional]`

    * **interval** *(type: object)* `[Optional]`

      * **start** *(type: long)* `[Optional]`

      * **end** *(type: long)* `[Optional]`

