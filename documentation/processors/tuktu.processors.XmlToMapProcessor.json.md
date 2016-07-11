### tuktu.processors.XmlToMapProcessor
Converts an XML node/document into a Map recursively.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the XML document.

    * **trim** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to trim the text if as_text is set to true.

    * **non_empty** *(type: boolean)* `[Optional, default = false]`
    - If set to true, keeps only non-empty children.

    * **flattened** *(type: boolean)* `[Optional, default = false]`
    - If set to true, returns the XML document as first-class citizen, otherwise adds the map as field resultName.

