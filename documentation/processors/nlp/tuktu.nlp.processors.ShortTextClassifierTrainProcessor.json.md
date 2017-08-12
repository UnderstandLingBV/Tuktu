### tuktu.nlp.processors.ShortTextClassifierTrainProcessor
Trains a short text classification model.

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
    - The field the data resides in. Data must be a string of words.

    * **label_field** *(type: string)* `[Required]`
    - The field the label is in. Value must be a double.

    * **features_to_add** *(type: array)* `[Optional]`
    - If given, the fields in this list specify additional feature vectors to add.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A field containing an additional feature vector.

    * **min_count** *(type: int)* `[Required]`
    - The minimum times a feature should be present.

    * **C** *(type: double)* `[Required]`
    - The cost of exceeding the penalty (1 - 100).

    * **epsilon** *(type: double)* `[Required]`
    - Regularization parameter.

    * **language** *(type: string)* `[Optional, default = "en"]`
    - The language of all the data inside the DP. Used for finding sentence boundaries.

    * **seed_word_file** *(type: string)* `[Required]`
    - The file that contains the seed words for this language. Can be computed via PMI for a specific label in a dataset.

    * **right_flip_file** *(type: string)* `[Required]`
    - The file that contains the right flips for this language.

    * **left_flip_file** *(type: string)* `[Required]`
    - The file that contains the left flips for this language.

