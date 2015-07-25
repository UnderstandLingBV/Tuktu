### tuktu.ml.processors.hmm.HMMDeserializeProcessor
Deserializes a Hidden Markov Model from disk to the in-memory model repository.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the HMM model to be deserialized.

    * **file_name** *(type: string)* `[Required]`
    - The file name to serialize to.

    * **only_once** *(type: string)* `[Optional, default = true]`
    - Whether or not to serialize only once. If set to true, the model will be serialized upon receival of the first DataPacket only, if set to false it will be overwritten for each new DataPacket.

    * **num_hidden** *(type: int)* `[Required]`
    - The number of hidden states of this HMM.

    * **num_observable** *(type: int)* `[Required]`
    - The number of observable states of this HMM.

    * **wait_for_load** *(type: boolean)* `[Optional, default = false]`
    - If set to true, processing only continues after the model has been loaded into memory (sync). If false, processing continues immediately, not knowing when the model has been materialized.

