### tuktu.nosql.processors.sql.SQLBulkProcessor
Inserts data into SQL in bulks (for efficiency). The entire DataPacket is inserted in one query.

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

    * **table** *(type: string)* `[Required]`
    - The name of the table to insert into.

    * **columns** *(type: array)* `[Optional]`
    - The (SQL) column names matching the data that is inserted.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - The name of the column.

    * **fields** *(type: array)* `[Optional]`
    - The names of the fields of the DataPacket from which data are read to be inserted.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - The name of the field.

    * **query_trail** *(type: string)* `[Optional, default = ""]`
    - A trail of the insert query that can optionally be given. Use for for example for ON DUPLICATE KEY strategy statements.

