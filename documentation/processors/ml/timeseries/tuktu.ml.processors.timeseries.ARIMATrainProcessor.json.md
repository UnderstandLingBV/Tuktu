### tuktu.ml.processors.timeseries.ARIMATrainProcessor
Trains a model using ARIMA (Autoregressive Integrated Moving Average) using Conditional Sum of Squares

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **wait_for_store** *(type: boolean)* `[Optional, default = false]`
    - Whether to wait for the model to be stored in the model repository. Setting this to true will ensure the model exists when proceeding to the next processor.

    * **p** *(type: int)* `[Required]`
    - p-parameter, the order of the autoregressive model.

    * **d** *(type: int)* `[Required]`
    - d-parameter, the degree of differencing.

    * **q** *(type: int)* `[Required]`
    - q-parameter, the order of the moving-average model.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

    * **include_intercept** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to include the intercept.

