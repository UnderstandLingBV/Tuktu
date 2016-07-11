### tuktu.aws.generators.S3BucketListerGenerator
Lists the keys/filenames of all files inside an S3 bucket, possibly recursive.

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

    * **file_name** *(type: string)* `[Required]`
    - The S3 address of the bucket (should be like s3://userid:userkey@bucketname/path/if/present).

    * **recursive** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to recurse into sub-buckets.

