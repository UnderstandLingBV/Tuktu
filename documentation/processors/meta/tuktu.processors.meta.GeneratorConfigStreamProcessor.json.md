### tuktu.processors.meta.GeneratorConfigStreamProcessor
Invokes a new generator for every DataPacket, which sends its data to a selected list of processors from a given config, replacing Tuktu config strings within.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the config so that you can follow it in the job overview.

    * **node** *(type: string)* `[Optional]`
    - The Tuktu SingleNode to execute on.

    * **next** *(type: array)* `[Required]`
    - The processors to send the data into.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **flow_field** *(type: string)* `[Required]`
    - Field containing the path of the flow to load relative to configs root folder; can contain variables which will be resolved with data packets' first datum.

    * **send_whole** *(type: boolean)* `[Optional]`
    - True sends the whole DataPacket, false sends each data object inside the DataPacket separately.

    * **replacements** *(type: array)* `[Optional]`
    - The replacements used to replace Tuktu config strings #{source} by target.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **source** *(type: string)* `[Required]`
        - The #{key} that will be replaced by the target string below in the invoked configs: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

        * **target** *(type: string)* `[Required]`
        - The replacement for the source above: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

