### tuktu.ml.processors.regression.LinearRegressionApplyProcessor
Applies a Linear regression model to data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

    * **estimate** *(type: boolean)* `[Optional, default = true]`
    - If set to true, the coefficients of the regression model are estimated before applying the model. If set to false, it is assumed that the model is deserialized from pre-stored coefficients.

