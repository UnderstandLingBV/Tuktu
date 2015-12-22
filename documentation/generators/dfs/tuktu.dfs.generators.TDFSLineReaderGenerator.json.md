### tuktu.dfs.generators.TDFSLineReaderGenerator
Reads a file from TDFS line by line

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
    - The name of the file to read.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The encoding to use.

    * **start_line** *(type: int)* `[Optional]`
    - The line to start reading from.

    * **end_line** *(type: int)* `[Optional]`
    - The last line to read. Entire file is read if none is given.

