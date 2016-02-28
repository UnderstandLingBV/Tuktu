### tuktu.generators.CustomPacketGenerator
Generates a custom tuktu data packet every tick

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

    * **interval** *(type: int)* `[Required]`
    - Tick interval in which to send the data packets, in milliseconds.

    * **packet** *(type: string)* `[Required]`
    - The tuktu data packet (expressed as a JSON array of JSON objects) to send every tick.

    * **json** *(type: boolean)* `[Optional, default = true]`
    - Keep values as JSON?

    * **send_immediately** *(type: boolean)* `[Optional]`
    - True if you want the initial data packet to be immediately send

    * **max_amount** *(type: int)* `[Optional]`
    - The maximum amount of data packets to be sent.

