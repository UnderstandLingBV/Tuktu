### tuktu.web.processors.HTTPGetProcessor
Makes a HTTP get request to a specific URL.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field that contains the URL(s) to get.

    * **timeout** *(type: int)* `[Optional]`
    - The time, expressed in seconds, after which an unsuccessful request times out

