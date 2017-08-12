### tuktu.nlp.processors.FastTextWordBasedClassifierProcessor
This classifier looks at vectors word-by-word and sees if there is a close-enough overlap between one or more candidate set words and the sentence's words.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - Name of the model to be applied. If a model with this name cannot be found, the data will go through unchanged.

    * **data_field** *(type: string)* `[Required]`
    - The field the data resides in. Data can be textual (String) or Seq[String].

    * **top** *(type: int)* `[Optional, default = 1]`
    - How many of the top classes to return.

    * **flatten** *(type: boolean)* `[Optional, default = true]`
    - If set, returns just the best scoring class.

    * **cutoff** *(type: string)* `[Optional]`
    - If set, only returns labels with a score higher than or equal to the cutoff. If no scores succeed, will return label -1 with score 0.0.

    * **candidate_field** *(type: string)* `[Optional]`
    - If set, this field in the DP must contain the candidate sets (as JSON, Seq[Seq[String]] or a String that is JSON). This will overwrite any values in the candidates list entered below.

    * **candidates** *(type: array)* `[Required]`
    - The candidate list.

      * **[UNNAMED]** *(type: array)* `[Required]`
      - Candidate words.

        * **[UNNAMED]** *(type: string)* `[Required]`
        - The candidate word (partially) defining this class.

