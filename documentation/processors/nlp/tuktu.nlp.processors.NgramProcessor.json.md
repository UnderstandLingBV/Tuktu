### tuktu.nlp.processors.NgramProcessor
Creates N-grams out of a piece of text (can be a sequence or a string).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to take N-grams from. This can be a String or a Seq[String].

    * **n** *(type: int)* `[Required]`
    - The length (N) of the N-grams.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to flatten the result. Normal result is a Seq[String]/Seq[Char].

    * **chars** *(type: boolean)* `[Optional, default = false]`
    - Whether to take character N-grams (true) or word N-grams (false). Only applies if the input is String (not Seq[String]).

