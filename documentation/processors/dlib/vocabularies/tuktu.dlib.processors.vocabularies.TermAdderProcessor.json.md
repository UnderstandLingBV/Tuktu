### tuktu.dlib.processors.vocabularies.TermAdderProcessor
Adds a term to a vocabulary of the in-memory vocabulary bank.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **vocabulary** *(type: string)* `[Required]`
    - The name of the vocabulary to which the new term must be added.

    * **source** *(type: string)* `[Required]`
    - The source term to add.

    * **target** *(type: string)* `[Required]`
    - The target term to add.

    * **language** *(type: string)* `[Optional]`
    - The language of the target term.

