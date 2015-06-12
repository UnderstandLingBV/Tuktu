### tuktu.nosql.processors.mongodb.MongoDBInsertProcessor
Inserts data into MongoDB.

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

    * **fields** *(type: array)* `[Required]`
    - The fields to be inserted: Leave empty to insert all fields.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Insert data in an (a)synchronous fashion.

