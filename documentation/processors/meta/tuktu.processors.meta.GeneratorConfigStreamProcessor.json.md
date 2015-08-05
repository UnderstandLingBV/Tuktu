### tuktu.processors.meta.GeneratorConfigStreamProcessor
Invokes a new generator, which sends its data to a selected list of processors from a given config.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the config.

    * **node** *(type: string)* `[Optional]`
    - The Tuktu SingleNode to execute on.

    * **next** *(type: array)* `[Required]`
    - The processors to send data into.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **flow_field** *(type: string)* `[Required]`
    - Field containing the name of the flow to load; can contain variables which will be resolved with data packets.

    * **send_whole** *(type: boolean)* `[Optional]`
    - True sends the whole DataPacket, false sends each data object inside the DataPacket separately.

