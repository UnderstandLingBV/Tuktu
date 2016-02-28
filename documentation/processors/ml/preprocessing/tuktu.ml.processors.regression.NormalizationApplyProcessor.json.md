### tuktu.ml.processors.preprocessing.NormalizationApplyProcessor
Applies a normalization model to data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **fields** *(type: array)* `[Optional]`
    - The fields to normalize.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - Field name.

