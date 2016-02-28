### tuktu.nlp.processors.PersuasionProcessor
Computes the persuasiveness (power to convince) of a message.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **language** *(type: string)* `[Required]`
    - The language to be used.

    * **tokens** *(type: string)* `[Required]`
    - The field that contains the tokens.

    * **pos** *(type: string)* `[Required]`
    - The field that contains the POS tags.

    * **emotions** *(type: string)* `[Required]`
    - The field that contains the emotions resulting from the RBEMEmotionProcessor.

