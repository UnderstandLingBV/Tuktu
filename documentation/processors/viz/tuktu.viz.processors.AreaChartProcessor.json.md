### tuktu.viz.processors.AreaChartProcessor
Takes data and produces a area chart end point.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the chart. If this is an existing name, data from multiple flows will be combined.

    * **historical** *(type: boolean)* `[Optional, default = false]`
    - If set to false, only streaming data will be shown. Set to true, all data received so far will be loaded on rendering of the graph.

    * **expiration** *(type: int)* `[Optional, default = false]`
    - Will clean up a chart's data if nothing was received within this time period.

    * **x_field** *(type: string)* `[Required]`
    - The name of the field containing the x-axis indicators.

    * **y_field** *(type: string)* `[Required]`
    - The name of the field containing the y-axis indicators.

