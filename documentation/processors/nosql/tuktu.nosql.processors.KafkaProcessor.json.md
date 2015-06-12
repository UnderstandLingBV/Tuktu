### tuktu.nosql.processors.KafkaProcessor
Sends keyed messages to a Kafka producer.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **kafka_props** *(type: JsObject)* `[Required]`
    - Kafka properties given by a JSON object with key, value pairs containing strings only.

    * **key_field** *(type: string)* `[Required]`
    - Field which contains the key for the keyed message.

