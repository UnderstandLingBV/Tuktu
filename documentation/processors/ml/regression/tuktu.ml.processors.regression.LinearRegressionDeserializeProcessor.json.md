### tuktu.ml.processors.regression.LinearRegressionDeserializeProcessor
Deserializes a linear regression model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be deserialized.

    * **file_name** *(type: string)* `[Required]`
    - The file name to deserialize from.

    * **only_once** *(type: string)* `[Optional, default = true]`
    - Whether or not to serialize only once. If set to true, the model will be serialized upon receival of the first DataPacket only, if set to false it will be overwritten for each new DataPacket.

