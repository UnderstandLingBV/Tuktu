### tuktu.nosql.processors.sql.RecursiveLookupProcessor
Fetches data recursively, provided an edge relation of a graph-like structure. (Without cycle detection.)

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

    * **columns** *(type: array)* `[Required]`
    - The columns to fetch from DB (changing its key from the column name to var) to use in ancestors's FROM and WHERE clauses.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **name** *(type: string)* `[Required]`
        - The column name.

        * **var** *(type: string)* `[Required]`
        - Replace the column name key with this var to use in ancestors' FROM and WHERE clauses.

    * **from** *(type: string)* `[Required]`
    - The FROM clause of the SQL statement.

    * **where** *(type: string)* `[Required]`
    - The WHERE clause of the SQL statement.

    * **include_original** *(type: boolean)* `[Optional, default = false]`
    - Include original data, or only return the data extended (potentially overwriting) by the found ancestors.

    * **n** *(type: int)* `[Optional]`
    - Number of iterations after which to stop. Leave empty to search exhaustively (beware: no cycle detection).

