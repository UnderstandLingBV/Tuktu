### tuktu.processors.meta.ParallelConfigProcessor
Starts parallel processor pipelines through generic configs, which are merged upon completion.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **merger** *(type: string)* `[Required]`
    - The merger class to be used to merge the results of the processors.

    * **include_original** *(type: boolean)* `[Optional, default = false]`
    - Merge the other results into the original DataPacket, or not.

    * **send_whole** *(type: boolean)* `[Optional, default = true]`
    - Send the whole DataPacket, or sends each datum inside the DataPacket separately. In the latter case, each datum will be processed and merged separately, and after that concatenated to form the new resulting DataPacket.

    * **timeout** *(type: int)* `[Optional]`
    - The duration in seconds to wait for the results of each individual DataPacket before timing out. If nothing is specified, the global timeout duration of the Tuktu node this flow is running on be used.

    * **pipelines** *(type: array)* `[Required]`
    - Each entry is defining a pipeline of processors for which an Enumeratee is built. The results of each is then merged using the merger class.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **start** *(type: array)* `[Required]`
        - The IDs of the processors to compose first. Due to current limitations, the result of the first processor in the processor flow after each start with not exactly one successor is used for merging, ie. no branching is supported.

          * **processor_id** *(type: string)* `[Required]`
          - The ID of the processor to compose first. Due to current limitations, the result of the first processor in the processor flow after this start with not exactly one successor is used for merging, ie. no branching is supported.

        * **config_path** *(type: string)* `[Required]`
        - The path of the config file.

    * **replacements** *(type: array)* `[Optional]`
    - The replacements used to replace Tuktu config strings #{source} by target.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **source** *(type: string)* `[Required]`
        - The #{key} that will be replaced by the target string below in the invoked configs: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

        * **target** *(type: string)* `[Required]`
        - The replacement for the source above: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

