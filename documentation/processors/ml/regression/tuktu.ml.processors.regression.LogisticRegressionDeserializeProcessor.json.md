### tuktu.ml.processors.regression.LogisticRegressionDeserializeProcessor
Deserializes a logistic regression model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be deserialized.

    * **file_name** *(type: string)* `[Required]`
    - The file name to serialize to.

    * **only_once** *(type: string)* `[Optional, default = true]`
    - Whether or not to serialize only once. If set to true, the model will be serialized upon receival of the first DataPacket only, if set to false it will be overwritten for each new DataPacket.

    * **learn_rate** *(type: int)* `[Required]`
    - The learning rate.

    * **num_iterations** *(type: int)* `[Required]`
    - The number of iterations of going over the data.

