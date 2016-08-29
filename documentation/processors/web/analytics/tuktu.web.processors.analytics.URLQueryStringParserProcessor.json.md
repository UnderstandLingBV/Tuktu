### tuktu.web.processors.analytics.URLQueryStringParserProcessor
Parses a query string obtained from a URL.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field that contains the URL to parse.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to add the query string parameters as top-level citizen (true) or not (false).

