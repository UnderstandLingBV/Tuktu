### tuktu.dlib.processors.oaipmh.ListMetadataFormatsProcessor
Retrieves the metadata formats available from a repository. An optional argument restricts the request to the formats available for a specific item.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target repository.

    * **identifier** *(type: string)* `[Optional]`
    - An optional argument that specifies the unique identifier of the item for which available metadata formats are being requested. If this argument is omitted, then the response includes all metadata formats supported by the target repository.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Converts XML set descriptions to JSON?

