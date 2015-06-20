### tuktu.nosql.processors.mongodb.MongoDBUpdateProcessor
Updates data into MongoDB.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **hosts** *(type: array)* `[Required]`
    - A list of node names, like node1.foo.com:27017. Port is optional, it is 27017 by default.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **database** *(type: string)* `[Required]`
    - The database name.

    * **collection** *(type: string)* `[Required]`
    - The name of the collection to open.

    * **query** *(type: string)* `[Required]`
    - The selection criteria for the update. 

    * **update** *(type: string)* `[Required]`
    - The modifications to apply.

    * **upsert** *(type: boolean)* `[Optional]`
    - If set to true, creates a new document when no document matches the query criteria. If set to false, does not insert a new document when no match is found.

    * **multi** *(type: boolean)* `[Optional]`
    - If set to true, updates multiple documents that meet the query criteria. If set to false, updates one document.

