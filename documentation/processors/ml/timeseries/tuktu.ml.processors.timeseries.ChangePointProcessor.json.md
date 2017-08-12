### tuktu.ml.processors.timeseries.ChangePointProcessor
Computes change points in a timeseries (observations as doubles)

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **key** *(type: array)* `[Required]`
    - The fields inside the DataPacket that, together, define the key to group on.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **timestamp_field** *(type: string)* `[Required]`
    - The field that contains the timestamp in the data.

    * **value_field** *(type: string)* `[Required]`
    - The field that contains the timeseries' value in the data.

    * **min_change** *(type: string)* `[Required]`
    - The minimum change that should occur to make a change count as a peak.

    * **min_ratio** *(type: string)* `[Required]`
    - The minimum ratio from last observation to new to make a change count as a peak.

    * **min_z_score** *(type: string)* `[Required]`
    - The minimum Z-score (normalized score) for a change to be called a peak.

    * **inactive_threshold** *(type: string)* `[Required]`
    - Threshold determining when a series of observations is called inactive.

    * **window_size** *(type: string)* `[Required]`
    - The amount of observations to take into account.

