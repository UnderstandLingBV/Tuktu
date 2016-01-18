### tuktu.dlib.processors.oaipmh.GetRecordProcessor
Retrieves an individual metadata record from a repository. Required arguments specify the identifier of the item from which the record is requested and the format of the metadata that should be included in the record.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **target** *(type: string)* `[Required]`
    - The OAI-PMH target repository.

    * **identifier** *(type: string)* `[Required]`
    - A required argument that specifies the unique identifier of the item in the repository from which the record must be disseminated.

    * **metadataPrefix** *(type: string)* `[Required]`
    - A required argument that specifies the metadataPrefix of the format that should be included in the metadata part of the returned record.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Converts XML description to JSON?

