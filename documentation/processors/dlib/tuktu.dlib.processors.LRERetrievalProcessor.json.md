### tuktu.dlib.processors.LRERetrievalProcessor
Retrieves Learning Resource Exchange (LRE) metadata and paradata records based on their identifiers.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **service** *(type: string)* `[Required]`
    - The LRE API endpoint.

    * **identifiers** *(type: string)* `[Required]`
    - A comma-separated list of the identifiers of the records to retrieve

    * **format** *(type: string)* `[Required]`
    - The format in which records must be returned.

