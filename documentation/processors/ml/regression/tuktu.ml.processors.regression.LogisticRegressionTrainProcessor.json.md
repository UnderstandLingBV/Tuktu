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

    * **learn_rate** *(type: int)* `[Required]`
    - The learning rate.

    * **num_iterations** *(type: int)* `[Required]`
    - The number of iterations of going over the data.

    * **batch_size** *(type: int)* `[Optional, default = 0]`
    - The batch size used to iteratively train the model, set to 0 to use all data.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Int].

    * **label_field** *(type: string)* `[Required]`
    - The field the label is in. Value must be an integer.

    * **wait_for_load** *(type: boolean)* `[Optional, default = false]`
    - If set to true, processing only continues after the model has been loaded into memory (sync). If false, processing continues immediately, not knowing when the model has been materialized.

