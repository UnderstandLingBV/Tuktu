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
 
    * **append** *(type: boolean)* `[Optional]`
    - Append the result to the data packet; otherwise the result is ignored.
 
