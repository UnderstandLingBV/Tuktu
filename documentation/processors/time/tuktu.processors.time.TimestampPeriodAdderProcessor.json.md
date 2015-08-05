### tuktu.processors.time.TimestampPeriodAdderProcessor
Adds (or substracts) a certain amount of time to a timefield.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **time_field** *(type: string)* `[Required]`
    - The field containing the timestamp to be modified.

    * **format** *(type: string)* `[Optional]`
    - If the timefield is a String, the formatting for this String.

    * **locale** *(type: string)* `[Optional]`
    - If the timefield is a String, the locale for this String.

    * **years** *(type: string)* `[Optional]`
    - Adds this amount of years to the timestamp.

    * **months** *(type: string)* `[Optional]`
    - Adds this amount of months to the timestamp.

    * **weeks** *(type: string)* `[Optional]`
    - Adds this amount of weeks to the timestamp.

    * **days** *(type: string)* `[Optional]`
    - Adds this amount of days to the timestamp.

    * **hours** *(type: string)* `[Optional]`
    - Adds this amount of hours to the timestamp.

    * **minutes** *(type: string)* `[Optional]`
    - Adds this amount of minutes to the timestamp.

    * **seconds** *(type: string)* `[Optional]`
    - Adds this amount of seconds to the timestamp.

