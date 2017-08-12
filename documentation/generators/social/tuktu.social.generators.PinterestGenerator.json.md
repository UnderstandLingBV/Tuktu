### tuktu.social.generators.PinterestGenerator
Gets all pins a specific board.

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

    * **key** *(type: string)* `[Required]`
    - The ID for the application.

    * **secret** *(type: string)* `[Required]`
    - The secret for the application.

    * **token** *(type: string)* `[Required]`
    - The access token.

    * **boards** *(type: array)* `[Required]`
    - The name of the boards. Should be <username>/<board>.

      * **[UNNAMED]** *(type: string)* `[Required]`
      - Board location, should be <username>/<board>.

    * **interval** *(type: object)* `[Optional]`
    - Time interval to collect data for.

      * **start** *(type: long)* `[Optional]`
      - Timestamp of start time of time interval to collect data for. Can be left empty. If a start-time is given, everything from that time on will be fetched. If it is omitted the current time is assumed.

      * **end** *(type: long)* `[Optional]`
      - Timestamp of end time of time interval to collect data for. Can be left empty. If an end-time is given, everything until that time will be fetched. If it is omitted, no end time is assumed and everything will be fetched perpetually.

    * **max_attempts** *(type: int)* `[Optional, default = 3]`
    - Maximum number of failed attempts for a specific board before giving up on it.

    * **update_time** *(type: int)* `[Optional, default = 5]`
    - Time in seconds between requests, if applicable.

    * **get_extended_user** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to get extended author profiles. Fetches a bit more fields on the author of a pin, but costs another request for every pin

