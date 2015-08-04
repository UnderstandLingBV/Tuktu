### tuktu.viz.processors.TimeLineProcessor
Takes data and produces a time line chart end point.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **time_field** *(type: string)* `[Optional]`
    - The name of the field containing the time indicators, for populating the horizontal axis. Must be a unix timestamp. If left out, uses the time the packet arrived.

    * **data_field** *(type: string)* `[Required]`
    - The name of the field containing the data (values), for populating the vertical axis.

