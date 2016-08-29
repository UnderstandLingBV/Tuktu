### tuktu.social.generators.FacebookGenerator
Gets posts containing keywords or from specific users from a given time interval.

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

    * **credentials** *(type: object)* `[Required]`

      * **access_token** *(type: string)* `[Required]`
      - The token to access Facebook's API with.

    * **filters** *(type: object)* `[Required]`

      * **users** *(type: array)* `[Optional]`
      - The users (given by their Facebook user id) from which to get everything from their feed, independently from keywords above.

        * **[UNNAMED]** *(type: string)* `[Required]`
        - Facebook user id.

    * **update_time** *(type: long)* `[Optional, default = 5]`
    - Time in seconds between requests, if applicable.

    * **interval** *(type: object)* `[Optional]`
    - Time interval to collect data for.

      * **start** *(type: long)* `[Optional]`
      - Timestamp of start time of time interval to collect data for. Can be left empty. If a start-time is given, everything from that time on will be fetched. If it is omitted the current time is assumed.

      * **end** *(type: long)* `[Optional]`
      - Timestamp of end time of time interval to collect data for. Can be left empty. If an end-time is given, everything until that time will be fetched. If it is omitted, no end time is assumed and everything will be fetched perpetually.

