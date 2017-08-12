### tuktu.nlp.processors.URBEMProcessor
Performs sentiment analtysis using the URBEM algorithm.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **language** *(type: string)* `[Required]`
    - The language to be used.

    * **tokens** *(type: string)* `[Required]`
    - The field that contains an Array of tokens.

    * **vector_file** *(type: string)* `[Required]`
    - The file that contains the fastText model.

    * **seed_words** *(type: JsObject)* `[Required]`
    - The seed words. A list of words per class. Keys of the object should contain the class names, the values should be JsArrays containing the words.

    * **right_flips** *(type: array)* `[Required]`
    - The right flip seed words.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A right flip seed word.

    * **left_flips** *(type: array)* `[Required]`
    - The left flip seed words.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A left flip seed word.

    * **seed_cutoff** *(type: string)* `[Required]`
    - The seed cutoff - between 0.0 and 1.0.

    * **negation_cutoff** *(type: string)* `[Required]`
    - The negation cutoff - between 0.0 and 1.0.

