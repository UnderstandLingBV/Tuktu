### tuktu.ml.processors.HMMTrainer
Trains a hidden markov model using observations.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **observations_field** *(type: string)* `[Required]`
    - The field which contains the observations as a sequence of integers. The HMM is sequentially trained on all these sequences within a bucket.

    * **steps** *(type: int)* `[Optional, default = 5]`
    - The number of steps to run the Baum-Welch algorithm on each sequence of integers within the bucket.

    * **num_hidden** *(type: int)* `[Required]`
    - The number of distinct hidden states.

    * **num_observable** *(type: int)* `[Required]`
    - The number of distinct observable states.

