### tuktu.nosql.processors.ESProcessor
Makes an HTTP request to Elastic Search, using the REST API.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - The HTTP address to make the request to.

    * **http_method** *(type: string)* `[Optional, default = "get"]`
    - Usually one of get, post, put, delete.

    * **body** *(type: any)* `[Optional]`
    - The body to be send with the request.

    * **field** *(type: string)* `[Required]`
    - The JSON field to be extracted from the response JSON.

