### tuktu.nosql.generators.SQLGenerator
Executes a query on an SQL database.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - A database url of the form jdbc:subprotocol:subname.

    * **user** *(type: string)* `[Required]`
    - The database user on whose behalf the connection is being made.

    * **password** *(type: string)* `[Required]`
    - The user's password.

    * **query** *(type: string)* `[Required]`
    - SQL query to be executed.

    * **driver** *(type: string)* `[Required]`
    - The driver to be used, for example org.h2.Driver.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Flatten the results, or return resultName -> results.

