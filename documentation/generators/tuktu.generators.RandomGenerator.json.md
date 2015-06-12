### tuktu.generators.RandomGenerator
Generates random integers.

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

    * **interval** *(type: int)* `[Required]`
    - Tick interval in milliseconds in which random numbers are to be generated.
 
    * **max** *(type: int)* `[Required]`
    - Random integers generated are uniformly distributed between 0 (inclusive) and the this value (exclusive).
 
