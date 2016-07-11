### tuktu.ml.processors.regression.RidgeRegressionApplyProcessor
Applies a Ridge regression model to data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

