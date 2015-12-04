### tuktu.ml.processors.MLDestroyProcessor
Removes a Machine Learning model from memory.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to destroy. Can contain Tuktu strings.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will models be cleaned up once EOF is reached, or after every DataPacket.

