### tuktu.dlib.processors.oaipmh.ListSetsProcessor
Retrieves the set structure of a repository.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target repository.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Converts XML set descriptions to JSON?

