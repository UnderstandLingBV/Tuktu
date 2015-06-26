### tuktu.ml.processors.hmm.HMMApplyDecodeProcessor
Applies a hidden markov model to find the most likely hidden state sequence given an observable state sequence.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **observations_field** *(type: string)* `[Required]`
    - The field which contains the observations as a sequence of integers. The Viterbi algorithm is used to find the most likely emission after each of these sequences of observations.

