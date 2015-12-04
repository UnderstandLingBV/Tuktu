### tuktu.nosql.processors.mongodb.MongoDBAggregateProcessor
Executes MongoDB aggregation pipeline.

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

    * **tasks** *(type: array)* `[Required]`
    - A list of tasks in the aggregation pipeline.  Note that currently, only the following tasks are supported: "$skip", "$limit", "$unwind", "$out", "$sort", "$match", "$project", and "$group".

      * **[UNNAMED]** *(type: JsObject)* `[Required]`

