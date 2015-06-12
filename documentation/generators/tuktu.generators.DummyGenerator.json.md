### tuktu.generators.DummyGenerator
Generates dummy strings every tick

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

      * **send_immediately** *(type: boolean)* `[Optional]`
      - True if you want the initial message to be immediately send

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **interval** *(type: int)* `[Required]`
    - Tick interval in which to send the messages, in milliseconds.

    * **message** *(type: string)* `[Required]`
    - The message to send every tick.

