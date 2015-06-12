### tuktu.nosql.processors.caching.H2Caching
Creates an in-memory H2 clone of a remote SQL database.

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

    * **db_name** *(type: string)* `[Required]`
    - The name of the database from which tables will be cloned.

    * **tables** *(type: array)* `[Required]`
    - The tables from the database above which will be cloned.

      * **[UNNAMED]** *(type: string)* `[Required]`

