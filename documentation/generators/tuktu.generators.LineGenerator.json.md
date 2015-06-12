### tuktu.generators.LineGenerator
Streams a file line by line.

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

    * **filename** *(type: string)* `[Required]`
    - The path to the file.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The encoding of the file.

    * **start_line** *(type: int)* `[Optional, default = 0]`
    - The start line to start streaming from.

    * **end_line** *(type: int)* `[Optional]`
    - The end line to stop streaming on, inclusively. Streamed until end of file if omitted.

