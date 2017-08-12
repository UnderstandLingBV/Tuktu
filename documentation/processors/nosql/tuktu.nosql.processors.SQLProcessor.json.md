### tuktu.nosql.processors.SQLProcessor
No description present.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - A database url of the form jdbc:subprotocol:subname.

    * **user** *(type: string)* `[Required]`
    - The database user on whose behalf the connection is being made.

    * **password** *(type: string)* `[Required]`
    - The user's password.

    * **driver** *(type: string)* `[Required]`
    - The driver to be used, for example org.h2.Driver.

    * **query** *(type: string)* `[Required]`
    - SQL query to be executed.

    * **append** *(type: boolean)* `[Optional, default = false]`
    - Append the result to the data packet; otherwise the result is ignored.

    * **separate** *(type: boolean)* `[Optional, default = true]`
    - If a select query is used and multiple result rows are given, either make a datum for each row (true) or concatenate all rows as a list in the datum (false).

    * **distinct** *(type: boolean)* `[Optional, default = false]`
    - Only execute distinct queries within each DataPacket once, and reuse results if append is true.

    * **clear_on_empty** *(type: boolean)* `[Optional, default = false]`
    - If append and separate are set to true, and if the result of the query is empty, this flag returns an empty datum (true) or just the original datum (false).

