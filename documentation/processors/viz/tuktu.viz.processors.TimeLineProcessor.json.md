### tuktu.viz.processors.TimeLineProcessor
Takes data and produces a time line chart end point.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the chart. If this is an existing name, data from multiple flows will be combined.

    * **historical** *(type: boolean)* `[Optional, default = false]`
    - If set to false, only streaming data will be shown. Set to true, all data received so far will be loaded on rendering of the graph.

    * **expiration** *(type: int)* `[Optional, default = false]`
    - Will clean up a chart's data if nothing was received within this time period.

    * **time_field** *(type: string)* `[Optional]`
    - The name of the field containing the time indicators, for populating the horizontal axis. Must be a unix timestamp. If left out, uses the time the packet arrived.

    * **data_field** *(type: string)* `[Required]`
    - The name of the field containing the data (values), for populating the vertical axis.

