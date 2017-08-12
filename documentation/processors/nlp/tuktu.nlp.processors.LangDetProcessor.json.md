### tuktu.nlp.processors.LangDetProcessor
Performs language detection on a given field. This algorithm supports more languages and is faster than LIGA but is less accurate.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to be performed language detection on.

    * **short_texts** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to load models tailored towards short texts. Less languages are supported for short texts.

