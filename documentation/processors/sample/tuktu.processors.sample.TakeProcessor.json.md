### tuktu.processors.sample.TakeProcessor
Takes a number of data packets (or datums per DP) and then terminates.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **amount** *(type: int)* `[Required]`
    - The amount of data packets (or datums) to take.

    * **datums** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the take is done on datums inside a DP, if set to false, it is performed on DPs.

