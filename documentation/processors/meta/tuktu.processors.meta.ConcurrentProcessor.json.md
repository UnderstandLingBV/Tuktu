### tuktu.processors.meta.ConcurrentProcessor
Takes a subflow and creates a number of instances of it. Data is then processed by one of the instances.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **instances** *(type: int)* `[Required]`
    - The number of instances to run per node.

    * **nodes** *(type: array)* `[Optional]`
    - The nodes to use. If left empty, all nodes are used.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - The hostname of the node.

    * **concurrent_name** *(type: string)* `[Required]`
    - The name of the concurrent processor. Only one unique concurrent processor with this name will exist on the cluster.

    * **ignore_results** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the concurrent processor should be the last processor in its branch. Results will not be fetched back from remote nodes, thus saving latency. Always leave to false unless this processor is the last.

    * **start** *(type: string)* `[Required]`
    - The ID of the processor to compose first.

    * **anchor_fields** *(type: array)* `[Optional]`
    - If you want to anchor datums, specify the fields to anchor on. Note that once anchoring is enable, DataPackets with multiple datums will inevitably be chopped up into DataPackets for each single datum of the original.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A field to anchor on.

    * **anchor_domain** *(type: array)* `[Optional]`
    - If you are anchoring on a single field, you can specify the (ALL) values that will occur. The processor will then forward round-robin based on the value occurrence rather than on a hashing function to ensure better fairness.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A value that the single anchor field can take.

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

