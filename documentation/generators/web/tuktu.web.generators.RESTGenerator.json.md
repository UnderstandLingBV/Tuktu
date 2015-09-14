### tuktu.web.generators.RESTGenerator
Makes a REST request and fetches the reply.

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

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - The URL to make a request to.

    * **port** *(type: int)* `[Optional, default = 80]`
    - The port number.

    * **http_method** *(type: string)* `[Optional, default = "get"]`
    - The HTTP method to use (post/put/delete/get).

    * **body** *(type: any)* `[Optional]`
    - The body to post (if applicable).

    * **add_code** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to add the HTTP status code obtained from the reply of the HTTP request to the DataPacket.

    * **code_field** *(type: string)* `[Optional, default = ""]`
    - If the HTTP status code is to be added, this is the result name of it.

    * **parse_as** *(type: string)* `[Optional, default = "text"]`
    - How to parse the result (JSON, XML, text).

