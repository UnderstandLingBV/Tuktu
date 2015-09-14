### tuktu.ml.processors.hmm.HMMTrainProcessor
Trains a hidden markov model using observations.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be trained. If a model with that name is already available, that model will be used for additional training. Otherwise a new model with this name will be instantiated.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **wait_for_store** *(type: boolean)* `[Optional, default = false]`
    - Whether to wait for the model to be stored in the model repository. Setting this to true will ensure the model exists when proceeding to the next processor.

    * **observations_field** *(type: string)* `[Required]`
    - The field which contains the observations as a sequence of integers. The HMM is sequentially trained on all these sequences within a bucket.

    * **num_hidden** *(type: int)* `[Required]`
    - The number of distinct hidden states.

    * **num_observable** *(type: int)* `[Required]`
    - The number of distinct observable states.

    * **priors** *(type: object)* `[Optional]`
    - The priors for the HMM matrices.

      * **transitions** *(type: array)* `[Optional]`
      - The priors of the transition matrix A.

        * **Rows** *(type: array)* `[Optional]`
        - The rows of the transition matrix.

          * **Column** *(type: float)* `[Optional]`
          - The actual value of the element.

      * **emissions** *(type: array)* `[Optional]`
      - The priors of the emission matrix B.

        * **Rows** *(type: array)* `[Optional]`
        - The rows of the emissions matrix.

          * **Column** *(type: float)* `[Optional]`
          - The actual value of the element.

      * **start** *(type: array)* `[Optional]`
      - The priors of the start matrix Pi.

        * **Rows** *(type: float)* `[Optional]`
        - The actual value of the element.

