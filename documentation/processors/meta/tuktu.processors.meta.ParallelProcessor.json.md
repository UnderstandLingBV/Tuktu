### tuktu.processors.meta.ParallelProcessor
Starts parallel processor pipelines, which are merged upon completion.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Optional]`
    - Name of these parallelly executed flows to identify in Monitor.

    * **merger** *(type: string)* `[Required]`
    - The merger class to be used to merge the results of the processors. For example:

tuktu.processors.merge.SimpleMerger
tuktu.processors.merge.JSMerger
tuktu.processors.merge.PaddingMerger
tuktu.processors.merge.SerialMerger

    * **include_original** *(type: boolean)* `[Optional, default = false]`
    - Merge the other results into the original DataPacket, or not.

    * **send_original** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to send the original source packet. If set to false, a dummy empty DataPacket is sent. Use with caution.

    * **processors** *(type: array)* `[Required]`
    - Each entry is defining a pipeline of processors for which an Enumeratee is built. The results of each is then merged using the merger class.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **start** *(type: string)* `[Required]`
        - The ID of the processor to compose first.

        * **pipeline** *(type: array)* `[Required]`
        - The actual pipeline of processors.

          * **[UNNAMED]** *(type: object)* `[Required]`

            * **id** *(type: string)* `[Required]`
            - The Id of the processor.

            * **name** *(type: string)* `[Required]`
            - The name of the processor.

            * **config** *(type: JsObject)* `[Required]`
            - The config of the processor.

            * **result** *(type: string)* `[Required]`
            - The result of the processor.

            * **next** *(type: array)* `[Required]`
            - The next processors to be composed. Due to current limitations, if this processor is the first one in the processor flow after start with not exactly one successor, its result is used for merging, ie. no branching is supported.

              * **[UNNAMED]** *(type: string)* `[Required]`
              - The next processor to be composed. Due to current limitations, if this processor is the first one in the processor flow after start with not exactly one successor, its result is used for merging, ie. no branching is supported.

