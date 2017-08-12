### tuktu.aws.processors.KinesisProcessor
Put record to kinesis stream

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - the url to push to.

    * **execution_timeout** *(type: int)* `[Optional, default = 30000]`
    - the execution timeout.

    * **request_timeout** *(type: int)* `[Optional, default = 30000]`
    - the request timeout.

    * **partition_key** *(type: string)* `[Optional]`
    - the key to partition the data.

    * **stream_name** *(type: string)* `[Required]`
    - the name of the stream to push to.

