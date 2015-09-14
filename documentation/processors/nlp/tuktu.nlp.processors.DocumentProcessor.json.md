### tuktu.nlp.processors.DocumentProcessor
Creates a document from multiple sentences in a DataPacket.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - Containing the strings that are the lines/sentences.

    * **separator** *(type: string)* `[Optional, default = " "]`
    - What to use as glue to merge all lines/sentences together.

    * **remove_empty_lines** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to remove lines that contain no characters.

