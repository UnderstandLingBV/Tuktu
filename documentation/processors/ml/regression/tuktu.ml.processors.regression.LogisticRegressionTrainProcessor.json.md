### tuktu.ml.processors.regression.LogisticRegressionTrainProcessor
Trains a logistic regression model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **wait_for_store** *(type: boolean)* `[Optional, default = false]`
    - Whether to wait for the model to be stored in the model repository. Setting this to true will ensure the model exists when proceeding to the next processor.

    * **lambda** *(type: double)* `[Optional, default = 0]`
    - The lambda parameter (shrinkage/regularization). Set to > 0 for regularization (typically generalizes better).

    * **tolerance** *(type: double)* `[Optional, default = 0.00001]`
    - The stopping tolerance criterium for BFGS.

    * **max_iterations** *(type: int)* `[Optional, default = 500]`
    - The maximum number of iterations.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

    * **label_field** *(type: string)* `[Required]`
    - The field the label is in. Value must be an integer.

