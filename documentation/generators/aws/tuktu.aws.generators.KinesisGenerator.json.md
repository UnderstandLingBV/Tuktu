### tuktu.aws.generators.KinesisGenerator
Reads JSON data from Kinesis.

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

    * **aws_access_key** *(type: string)* `[Optional]`
    - Sets the AWS access key if it cannot be found.

    * **aws_access_secret** *(type: string)* `[Optional]`
    - Sets the AWS access secret if it cannot be found.

    * **aws_region** *(type: string)* `[Optional, default = "eu-west-1"]`
    - Sets the AWS region.

    * **stream_name** *(type: string)* `[Required]`
    - The Kinesis stream name.

    * **app_name** *(type: string)* `[Required]`
    - The name of the Kinesis consumer application.

    * **initial_position** *(type: string)* `[Optional, default = "latest"]`
    - The initial position in the stream. Allowed values are horizon and latest.

    * **retry_count** *(type: int)* `[Optional, default = 3]`
    - The amount of retries before giving up.

    * **backoff_time** *(type: long)* `[Optional, default = 1000]`
    - The time in milliseconds to back-off before retrying.

    * **checkpoint_interval** *(type: long)* `[Optional, default = 1000]`
    - The interval determining how frequently to checkpoint.

