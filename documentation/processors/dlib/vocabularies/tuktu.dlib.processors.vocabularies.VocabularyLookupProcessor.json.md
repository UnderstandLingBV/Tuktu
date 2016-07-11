### tuktu.dlib.processors.vocabularies.VocabularyLookupProcessor
Looks up a vocabulary term in a vocabulary of the in-memory vocabulary bank.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **term** *(type: string)* `[Required]`
    - The source term to look up in the vocabulary.

    * **language** *(type: string)* `[Optional]`
    - The language of the target term.

    * **name** *(type: string)* `[Required]`
    - The name of the vocabulary.

