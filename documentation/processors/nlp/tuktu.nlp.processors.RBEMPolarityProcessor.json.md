### tuktu.nlp.processors.RBEMPolarityProcessor
Performs polarity detection given a language, an Array of tokens and an Array of POS tags.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **language** *(type: string)* `[Required]`
    - The language to be used.

    * **tokens** *(type: string)* `[Required]`
    - The field that contains an Array of tokens.

    * **pos** *(type: string)* `[Required]`
    - The field that contains an Array of POS tags.

    * **discretize** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to return discrete (1.0, 0.0, -1.0) or the actual continuous forms.

