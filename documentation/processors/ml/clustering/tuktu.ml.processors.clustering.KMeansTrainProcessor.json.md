### tuktu.ml.processors.clustering.KMeansTrainProcessor
Trains a K-Means model - basically computes the centroids on the given data.

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

    * **k** *(type: int)* `[Required]`
    - The number of clusters to find.

    * **max_iterations** *(type: int)* `[Optional]`
    - The maximum number of iterations to run before early-stopping.

    * **runs** *(type: int)* `[Required]`
    - The number of runs of the K-means algorithm.

