### tuktu.nosql.generators.MongoDBAggregateGenerator
Executes tasks in an aggregation pipeline on a given list of nodes.

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

    * **hosts** *(type: array)* `[Required]`
    - A list of node names, like node1.foo.com:27017. Port is optional, it is 27017 by default.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **db** *(type: string)* `[Required]`
    - The database to query against.

    * **collection** *(type: string)* `[Required]`
    - The name of the collection to query.

    * **mongo_options** *(type: object)* `[Optional]`
    - All possible mongo options, all optional.

      * **connectTimeoutMS** *(type: int)* `[Optional, default = 0]`
      - The number of milliseconds to wait for a connection to be established before giving up.

      * **authSource** *(type: string)* `[Optional]`
      - The database source for authentication credentials.

      * **sslEnabled** *(type: boolean)* `[Optional, default = false]`
      - It enables the SSL support for the connection.

      * **sslAllowsInvalidCert** *(type: boolean)* `[Optional, default = false]`
      -  If sslEnabled is true, this one indicates whether to accept invalid certificates (e.g. self-signed).

      * **authMode** *(type: string)* `[Optional, default = "cr"]`
      - The authentication mode. By default, it is the backward compatible MONGODB-CR which is used. If this options is set to sha1, then the SCRAM-SHA-1 authentication will be selected.

      * **tcpNoDelay** *(type: boolean)* `[Optional, default = false]`
      - TCPNoDelay boolean flag.

      * **keepAlive** *(type: boolean)* `[Optional, default = false]`
      - TCP KeepAlive boolean flag.

      * **nbChannelsPerNode** *(type: int)* `[Optional, default = 10]`
      - Number of channels (connections) per node.

      * **writeConcern** *(type: string)* `[Optional, default = "acknowledged"]`
      - The default write concern (default: acknowledged). unacknowledged: Option w set to 0, journaling off (j), fsync off, no timeout. acknowledged: Option w set to 1, journaling off, fsync off, no timeout. journaled: Option w set to 1, journaling on, fsync off, no timeout.

      * **readPreference** *(type: string)* `[Optional, default = "primary"]`
      - The default read preference (primary|primaryPreferred|secondary|secondaryPreferred|nearest) - see http://reactivemongo.org/releases/0.11/documentation/advanced-topics/read-preferences.html.

    * **auth** *(type: object)* `[Optional]`
    - The authentication credentials if authentication is used.

      * **db** *(type: string)* `[Optional]`
      - The authentication database name.

      * **user** *(type: string)* `[Optional]`
      - The username.

      * **password** *(type: string)* `[Optional]`
      - The password.

    * **tasks** *(type: array)* `[Required]`
    - A list of aggregation tasks for the pipeline.

      * **[UNNAMED]** *(type: JsObject)* `[Required]`

    * **batch** *(type: boolean)* `[Optional, default = false]`
    - Are all results to be batched before pushing it on the channel.

