### tuktu.dlib.processors.XSLTProcessor
Generates a text output based on a XSL and changing XML data.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **xsl** *(type: string)* `[Required]`
    - The URL at which the XSL to apply can be found.

    * **encodings** *(type: string)* `[Optional, default = "UTF-8"]`
    - The encodings of the XSL file.

    * **xml** *(type: string)* `[Required]`
    - The field containing the changing XML data to which the XSL transformation must be applied.

