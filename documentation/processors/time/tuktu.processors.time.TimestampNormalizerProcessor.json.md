### tuktu.processors.time.TimestampNormalizerProcessor
Floors a given timestamp, based on the time fields; e.g. floored to the nearest 10 minutes.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **datetime_format** *(type: string)* `[Optional]`
    - The datetime format to parse the given field with. Can be left empty if field is already a datetime object or a long timestamp.

    * **datetime_field** *(type: string)* `[Required]`
    - The field which contains a formatted datetime, a datetime object, or a long timestamp.

    * **datetime_locale** *(type: string)* `[Optional, default = "en"]`
    - The locale of the datetime format to parse the field with.

    * **time** *(type: object)* `[Required]`

      * **years** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **months** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Month of Year, so should divide 12 if equally sized time-frames are desired. A value of 5 would mean 1-5, 6-10, 11-12 are the resulting time-frames. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **weeks** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Week of Year based on the ISO week year, in which a year can have either 52 or 53 weeks, and the first week of the year can even start in the previous year if the majority of days during that week are still in the current year. Most of the time only a value of 1 makes sense here, which will round down the timestamp to Monday at midnight. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **days** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Day of Year, and since a year usually consists of 365 or 366 days, only a value of 1 will yield equally sized time-frames across years. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **hours** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Hour of Day, which means equally sized time-frames are only guaranteed if this value divides 24. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **minutes** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Minute of Day, which means equally sized time-frames are only guaranteed if this value divides 24*60=1440. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **seconds** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Second of Day, which means equally sized time-frames are only guaranteed if this value divides 24*60*60=86400. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

      * **millis** *(type: int)* `[Optional]`
      - Floor the timestamp to the nearest time frame. Milliseconds of Day, which means equally sized time-frames are only guaranteed if this value divides 24*60*60*1000=86400000. Only takes largest unit into account, so if 10 years and 10 hours is present, only 10 years is used.

