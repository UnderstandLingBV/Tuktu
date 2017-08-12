### tuktu.ml.processors.decisiontrees.GradientTreeBoostTrainProcessor
Trains a Gradient Tree Boost model.

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
    - The field the data resides in. Data must be of type Seq[Int].

    * **label_field** *(type: string)* `[Required]`
    - The field the label is in. Value must be an integer.

    * **num_trees** *(type: int)* `[Required]`
    - The number of trees.

    * **max_nodes** *(type: int)* `[Optional, default = 6]`
    - The maximum number of leave nodes. More nodes means higher model complexity, implying longer training times but generally stronger models. Values between 4 and 8 typically work well.

    * **shrinkage** *(type: double)* `[Optional, default = 0.005]`
    - Shrinkage is a regularization parameter that helps prevent overfitting (learning rate). Typically low values under 0.1 yield great benefits compared to no shrinkage (a value of 1). The lower this value, the longer it takes to train the model.

    * **sampling_rate** *(type: double)* `[Optional, default = 0.7]`
    - Setting the sampling rate will train a subtree only on a portion of the data instead of the entire dataset, usually increasing generalization.

