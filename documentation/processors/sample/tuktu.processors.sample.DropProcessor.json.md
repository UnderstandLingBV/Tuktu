### tuktu.processors.sample.DropProcessor
Drops some data packets (or datums) before sending through.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **amount** *(type: int)* `[Required]`
    - The amount of data packets (or datums) to drop.

    * **datums** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the drop is done on datums inside a DP, if set to false, it is performed on DPs.

