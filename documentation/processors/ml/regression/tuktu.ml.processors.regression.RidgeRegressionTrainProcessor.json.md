### tuktu.ml.processors.regression.RidgeRegressionTrainProcessor
Trains a Ridge regression model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **wait_for_store** *(type: boolean)* `[Optional, default = false]`
    - Whether to wait for the model to be stored in the model repository. Setting this to true will ensure the model exists when proceeding to the next processor.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

    * **label_field** *(type: string)* `[Required]`
    - The field the label is in. Value must be a double.

    * **lambda** *(type: double)* `[Required]`
    - The shrinkage/regularization parameter (must be bigger than or equal to 0).

    * **train_on_new_data** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the model is retrained every time new data is added. Can be costly in streaming settings. If set to false, the model is only trained upon serialization.

