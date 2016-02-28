### tuktu.web.processors.URLCheckerProcessor
Returns the HTTP response status of a URL (returns -1 if the URL times out).  If a list of valid status codes is provided, returns true if the actual status is contained in the list and false otherwise (or if the URL times out).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - The URL to check.

    * **codes** *(type: array)* `[Optional]`
    - A list of HTTP response status codes considered as a success.

      * **[UNNAMED]** *(type: int)* `[Required]`

    * **field** *(type: string)* `[Optional]`
    - The name of a field containing the list of HTTP response status codes considered as a success. Note that, when this field exists, parameter 'codes' is ignored.

