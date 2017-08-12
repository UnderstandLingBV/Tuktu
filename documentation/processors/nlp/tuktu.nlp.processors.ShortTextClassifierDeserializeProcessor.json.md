### tuktu.nlp.processors.ShortTextClassifierDeserializeProcessor
Deserializes a short text classification model.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be deserialized.

    * **file_name** *(type: string)* `[Required]`
    - The file name to deserialize from.

    * **only_once** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to serialize only once. If set to true, the model will be serialized upon receival of the first DataPacket only, if set to false it will be overwritten for each new DataPacket.

    * **wait_for_load** *(type: boolean)* `[Optional, default = false]`
    - If set to true, processing only continues after the model has been loaded into memory (sync). If false, processing continues immediately, not knowing when the model has been materialized.

    * **min_count** *(type: double)* `[Required]`
    - The minimum fraction a feature should be present.

