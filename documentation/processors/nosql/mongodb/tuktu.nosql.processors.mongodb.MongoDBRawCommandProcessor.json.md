### tuktu.nosql.processors.mongodb.MongoDBRawCommandProcessor
Runs a command on the specified database on a given list of nodes.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **hosts** *(type: array)* `[Required]`
    - A list of node names, like node1.foo.com:27017. Port is optional, it is 27017 by default.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **db** *(type: string)* `[Required]`
    - The database to query against.

    * **command** *(type: JsObject)* `[Required]`
    - The command to run on the database. If you want to insert a JsObject using Tuktu Strings, you can do: $JSON.parse{${jsObject}}, or if you want to convert a Map to a JsObject on the fly: $JSON.parse{$JSON.stringify{map}}. Works not only on top-level, but also as a value within an object as JsString, and not only on maps, for example: {"${key}": "$JSON.parse{$JSON.stringify{list}}"} (Remember that keys never need to and hence can not be parsed as JSON, since they need to be strings.)

    * **resultOnly** *(type: boolean)* `[Optional, default = false]`
    - Only returns the result part of the command output?

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

