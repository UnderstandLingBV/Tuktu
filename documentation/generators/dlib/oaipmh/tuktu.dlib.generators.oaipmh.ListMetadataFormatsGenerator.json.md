### tuktu.dlib.generators.oaipmh.ListMetadataFormatsGenerator
Retrieves the metadata formats available from a repository. An optional argument restricts the request to the formats available for a specific item.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target repository.

    * **identifier** *(type: string)* `[Optional]`
    - An optional argument that specifies the unique identifier of the item for which available metadata formats are being requested. If this argument is omitted, then the response includes all metadata formats supported by the target repository.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Converts XML set descriptions to JSON?

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Flattens JSON set descriptions?

