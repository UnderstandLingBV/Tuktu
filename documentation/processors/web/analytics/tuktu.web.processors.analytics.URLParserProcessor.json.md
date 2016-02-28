### tuktu.web.processors.analytics.URLParserProcessor
Parses a URL into a map containing key/value pairs that explain the URL content. The following keys are added: protocol, authority, host, port, path, query, filename, ref.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field that contains the URL to parse.

