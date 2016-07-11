### tuktu.ml.processors.regression.LogisticRegressionDeserializeProcessor
Deserializes a logistic regression model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be deserialized.

    * **file_name** *(type: string)* `[Required]`
    - The file name to serialize to.

    * **only_once** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to serialize only once. If set to true, the model will be serialized upon receival of the first DataPacket only, if set to false it will be overwritten for each new DataPacket.

    * **wait_for_load** *(type: boolean)* `[Optional, default = false]`
    - If set to true, processing only continues after the model has been loaded into memory (sync). If false, processing continues immediately, not knowing when the model has been materialized.

    * **lambda** *(type: double)* `[Optional, default = 0]`
    - The lambda parameter (shrinkage/regularization). Set to > 0 for regularization (typically generalizes better).

    * **tolerance** *(type: double)* `[Optional, default = 0.00001]`
    - The stopping tolerance criterium for BFGS.

    * **max_iterations** *(type: int)* `[Optional, default = 500]`
    - The maximum number of iterations.

