### tuktu.nlp.processors.ShortTextClassifierApplyProcessor
Applies a short text model to text.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data must be of type Seq[Double].

    * **features_to_add** *(type: array)* `[Optional]`
    - If given, the fields in this list specify additional feature vectors to add.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A field containing an additional feature vector.

    * **language** *(type: string)* `[Optional, default = "en"]`
    - The language of all the data inside the DP. Used for finding sentence boundaries.

    * **default_class** *(type: string)* `[Optional]`
    - If the document bears too little information (less than 10 characters), this default class will be returned. Must be an integer. If not given, -1.0 is returned in cases where the text is too small.

