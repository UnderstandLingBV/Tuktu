### tuktu.dlib.processors.EuropeanaQueryProcessor
Queries the Europeana API and returns pointers to the resulting records.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **query** *(type: string)* `[Required]`
    - A Europeana API query.

    * **apikey** *(type: string)* `[Required]`
    - A Europeana API key.

    * **maxresult** *(type: int)* `[Optional]`
    - The maximum number of results to be returned by each query.

