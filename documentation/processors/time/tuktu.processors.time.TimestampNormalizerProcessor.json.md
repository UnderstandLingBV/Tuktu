### tuktu.processors.time.TimestampNormalizerProcessor
Floors a given timestamp, based on the time fields; e.g. floored to the nearest 10 minutes.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **datetime_format** *(type: string)* `[Required]`

    * **datetime_field** *(type: string)* `[Required]`

    * **datetime_locale** *(type: string)* `[Required]`

    * **overwrite** *(type: boolean)* `[Optional]`

    * **time** *(type: object)* `[Required]`

      * **years** *(type: int)* `[Optional]`

      * **months** *(type: int)* `[Optional]`

      * **days** *(type: int)* `[Optional]`

      * **hours** *(type: int)* `[Optional]`

      * **minutes** *(type: int)* `[Optional]`

      * **seconds** *(type: int)* `[Optional]`

      * **millis** *(type: int)* `[Optional]`

