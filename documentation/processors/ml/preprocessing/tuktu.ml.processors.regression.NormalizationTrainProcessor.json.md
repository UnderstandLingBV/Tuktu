### tuktu.ml.processors.preprocessing.NormalizationTrainProcessor
Trains a normalization model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **wait_for_store** *(type: boolean)* `[Optional, default = false]`
    - Whether to wait for the model to be stored in the model repository. Setting this to true will ensure the model exists when proceeding to the next processor.

    * **fields** *(type: array)* `[Optional]`
    - The fields to normalize.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - Field name.

    * **min** *(type: double)* `[Optional, default = 0]`
    - The lower bound to normalize to (0.0 if not set).

    * **max** *(type: double)* `[Optional, default = 1]`
    - The upper bound to normalize to (1.0 if not set).

