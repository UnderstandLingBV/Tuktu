### tuktu.dlib.processors.oaipmh.HarvesterProcessor
Harvests metadata records (ListRecords) or metadata records identifiers (ListIdentifiers) from an OAI-PMH target repository.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **identifiersOnly** *(type: boolean)* `[Required]`
    - Only harvest the record identifiers (ListIdentifiers) instead of the full metadata records (ListRecords)?

    * **target** *(type: string)* `[Required]`
    - The URL of the OAI-PMH target to harvest.

    * **metadataPrefix** *(type: string)* `[Required]`
    - A required argument that specifies the metadataPrefix of the format that should be included in the metadata part of the returned records (e.g., Dublin Core: oai_dc, IEEE LOM: oai_lom).

    * **from** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a lower bound for datestamp-based selective harvesting.

    * **until** *(type: string)* `[Optional]`
    - An optional argument with a UTCdatetime value, which specifies a upper bound for datestamp-based selective harvesting.

    * **set** *(type: string)* `[Optional]`
    - An optional argument that specifies the set to selectively harvest.

    * **toJSON** *(type: boolean)* `[Optional, default = false]`
    - Convert harvested XML records to JSON?

