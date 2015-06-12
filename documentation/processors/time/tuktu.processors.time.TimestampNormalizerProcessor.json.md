### tuktu.processors.time.TimestampNormalizerProcessor
Floors a given timestamp, based on the time fields; e.g. floored to the nearest 10 minutes.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **datetime_format** *(type: string)* `[Required]`
    - The datetime format to parse the given field with.
 
    * **datetime_field** *(type: string)* `[Required]`
    - The field which contains a formatted datetime.
 
    * **datetime_locale** *(type: string)* `[Required]`
    - The locale of the datetime format to parse the field with.
 
    * **overwrite** *(type: boolean)* `[Optional]`
    - Overwrite the datetime field with the normalized timestamp, or save it under result.
 
    * **time** *(type: object)* `[Required]`

      * **years** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **months** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **days** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **hours** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **minutes** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **seconds** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
      * **millis** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.
 
