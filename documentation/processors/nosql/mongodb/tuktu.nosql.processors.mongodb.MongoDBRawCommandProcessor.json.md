### tuktu.nosql.processors.mongodb.MongoDBRawCommandProcessor
Runs a command on the specified database on a given list of nodes.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **hosts** *(type: array)* `[Required]`
    - A list of node names, like node1.foo.com:27017. Port is optional, it is 27017 by default.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **database** *(type: string)* `[Required]`
    - The database name.

    * **command** *(type: JsObject)* `[Required]`
    - The command to run on the database.

    * **resultOnly** *(type: boolean)* `[Optional, default = false]`
    - Only returns the result part of the command output?

