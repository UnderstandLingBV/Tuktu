### tuktu.nlp.processors.SummarizeProcessor
Summarizes a piece of text based on TF-IDF scores.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **text_field** *(type: string)* `[Required]`
    - The field containing the text to summarize.

    * **tfidf_field** *(type: string)* `[Required]`
    - The field containing the TF-IDF scores. Use the TF-IDF processor to compute.

    * **num_lines** *(type: int)* `[Required]`
    - The number of lines the summarization text should contain.

    * **return_plain_text** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to return a plain text or a list of all sentences of the summary.

    * **optimal_sentence_length** *(type: int)* `[Optional, default = 11]`
    - The optimal length of sentence to include in the memory. The summarize processor will try to find sentence of length closest to this value.

    * **base** *(type: int)* `[Optional, default = 1.1]`
    - The base number to use for scaling (Double). Must be bigger than 1.0 where 1.0 means no scaling and the higher this number the more aggressive the scaling.

    * **preserve_order** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to preserve the order of sentences returned in the summary as they appeared in the original document.

