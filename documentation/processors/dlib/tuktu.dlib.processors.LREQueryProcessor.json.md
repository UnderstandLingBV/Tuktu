### tuktu.dlib.processors.LREQueryProcessor
Queries the Learning Resource Exchange (LRE) REST API and returns the identifiers of the resulting records.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **service** *(type: string)* `[Required]`
    - The LRE API endpoint.

    * **query** *(type: string)* `[Required]`
    - A CNF query.

    * **limit** *(type: int)* `[Optional]`
    - The maximum number of results to be returned by each query.

    * **resultOnly** *(type: boolean)* `[Required]`
    - Only returns record identifiers?

