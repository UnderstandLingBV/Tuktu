### tuktu.nlp.processors.LineSplitterProcessor
Splits a text up in lines (of minimum size)

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the text to split.

    * **min_size** *(type: int)* `[Optional, default = 1]`
    - The minimum character count per line that should be kept.

    * **separate** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to separate lines into elements of the DataPacket. If set to false, a single piece of text will be kept.

