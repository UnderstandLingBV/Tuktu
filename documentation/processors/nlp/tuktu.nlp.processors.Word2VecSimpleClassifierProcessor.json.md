### tuktu.nlp.processors.Word2VecSimpleClassifierProcessor
Classifies a document by comparing it's words to a set of candidate word sets (one set of words per class).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **destroy_on_eof** *(type: boolean)* `[Optional, default = true]`
    - Will this model be cleaned up once EOF is reached.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data can be textual (String) or Seq[String].

    * **candidates** *(type: array)* `[Required]`
    - The candidate list.

      * **[UNNAMED]** *(type: array)* `[Required]`
      - Candidate words.

        * **[UNNAMED]** *(type: string)* `[Required]`
        - The candidate word (partially) defining this class.

    * **top** *(type: int)* `[Optional, default = 1]`
    - How many of the top classes to return.

    * **flatten** *(type: boolean)* `[Optional, default = true]`
    - If set, returns just the best scoring class.

    * **cutoff** *(type: double)* `[Optional]`
    - If set, only returns labels with a score higher than or equal to the cutoff. If no scores succeed, will return label -1 with score 0.0.

