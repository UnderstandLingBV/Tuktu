### tuktu.processors.AkkaSenderProcessor
Sends a DataPacket's content to an Akka actor given by an actor path.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **actor_path** *(type: string)* `[Required]`
    - The path of the actor to send the DataPacket's contents to.

