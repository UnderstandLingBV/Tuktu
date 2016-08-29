### tuktu.nosql.generators.KafkaGenerator
Creates a Kafka consumer generating data packets from the feed of messages for a given topic.

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

    * **kafka_properties** *(type: JsObject)* `[Required]`
    - Kafka properties given by a JSON object with key, value pairs containing strings only.

    * **topic** *(type: string)* `[Required]`
    - The topic that messages are to be fetched about.

    * **stop_message** *(type: string)* `[Optional]`
    - Stop as soon as this message is encountered. Ignored if to_string is false.

    * **to_string** *(type: boolean)* `[Optional, default = true]`
    - Convert the message to a string, or keep it as a byte array.

    * **charset** *(type: string)* `[Optional, default = "utf-8"]`
    - The charset used to convert the message to a string. Ignored if to_string is false.

    * **threads** *(type: int)* `[Optional, default = 1]`
    - Number of threads to read partitions with.

