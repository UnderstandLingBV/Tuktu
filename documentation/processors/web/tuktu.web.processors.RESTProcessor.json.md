### tuktu.web.processors.RESTProcessor
Makes a REST request to a specific URL.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - The URL to make a request to.

    * **http_method** *(type: string)* `[Optional, default = "get"]`
    - The HTTP method to use (post/put/delete/get).

    * **body** *(type: any)* `[Optional]`
    - The body to post (if applicable).

