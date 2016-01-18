### tuktu.dlib.generators.oaipmh.ListIdentifiersGenerator
Harvests metadata record identifiers from an OAI-PMH target repository.

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

  * **config** *(type: object)* `[Required]`

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target to harvest.

    * **metadataPrefix** *(type: string)* `[Required]`
    - A required argument that specifies the metadataPrefix of the format that should be included in the metadata part of the returned records (e.g., Dublin Core: oai_dc, IEEE LOM: oai_lom).

    * **from** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a lower bound for datestamp-based selective harvesting.

    * **until** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a upper bound for datestamp-based selective harvesting.

    * **sets** *(type: array)* `[Optional]`
    - An optional argument that specifies the set(s) to selectively harvest.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Convert harvested XML headers to JSON?

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Flatten JSON headers?

