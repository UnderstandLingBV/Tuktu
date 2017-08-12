### tuktu.viz.processors.BaseChartProcessor
Base chart processor that simply sends JSON data to a websocket connection so you can plug in your own charting/viz library.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the chart. If this is an existing name, data from multiple flows will be combined.

    * **historical** *(type: boolean)* `[Optional, default = false]`
    - If set to false, only streaming data will be shown. Set to true, all data received so far will be loaded on rendering of the graph.

    * **expiration** *(type: int)* `[Optional, default = false]`
    - Will clean up a chart's data if nothing was received within this time period.

    * **field** *(type: string)* `[Required]`
    - The name of the field containing the JSON element.

