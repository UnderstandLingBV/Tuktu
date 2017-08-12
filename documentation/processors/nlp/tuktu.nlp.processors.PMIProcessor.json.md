### tuktu.nlp.processors.PMIProcessor
Computes PMIs of given seed words and a corpus of documents.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **words** *(type: array)* `[Required]`
    - The seed words to compute the PMI scores for.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - A seed word to compute the PMI scores for.

    * **language** *(type: string)* `[Required]`
    - Language of the text.

    * **document_field** *(type: string)* `[Required]`
    - The field containing the documents.

    * **label_field** *(type: string)* `[Required]`
    - The field containing the labels.

    * **retain** *(type: int)* `[Required]`
    - The amount of words with highest PMI to retain.

