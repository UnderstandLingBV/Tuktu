### tuktu.nosql.processors.mongodb.MongoDBRemoveProcessor
Removes data from MongoDB.

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

    * **user** *(type: string)* `[Optional]`
    - The name of the user (if authentication is required).

    * **password** *(type: string)* `[Optional]`
    - The password of the user (if authentication is required).

    * **admin** *(type: boolean)* `[Required]`
    - Does authentication use the admin database?

    * **ScramSha1** *(type: boolean)* `[Required]`
    - Use the ScramSha1 authentication method (instead of CrAuth)?

    * **query** *(type: string)* `[Required]`
    - The deletion query.

    * **just_one** *(type: boolean)* `[Optional]`
    - Delete only one item?

    * **blocking** *(type: boolean)* `[Optional, default = true]`
    - Wait for deletion to complete?

    * **timeout** *(type: int)* `[Optional, default = 5]`
    - Overwrite the Tuktu default timeout.

