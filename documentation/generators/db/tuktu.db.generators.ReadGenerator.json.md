### tuktu.db.generators.ReadGenerator
Reads data from a Tuktu DB bucket.

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

    * **keys** *(type: array)* `[Required]`
    - The keys that together from the keystring of the bucket.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **as_block** *(type: boolean)* `[Optional, default = false]`
    - If set to true, outputs the entire bucket in one DataPacket, if set to false, each element in the bucket is emitted separately.

