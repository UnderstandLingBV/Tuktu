### tuktu.generators.TimeGenerator
Generates points of time by adding (or subtracting) a certain amount of time to a starting time until a ending time is reached.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **starting_time** *(type: string)* `[Required]`
    - The starting time. Use now() to get current time.

    * **ending_time** *(type: string)* `[Optional]`
    - The ending time. Use now() to get current time.

    * **format** *(type: string)* `[Optional]`
    - The format in which the starting and ending times are expressed.

    * **locale** *(type: string)* `[Optional]`
    - The locale for the starting and ending times.

    * **years** *(type: string)* `[Optional]`
    - Adds or substracts this amount of years to the starting time until the ending time is reached.

    * **months** *(type: string)* `[Optional]`
    - Adds or substracts this amount of months to the starting time until the ending time is reached.

    * **weeks** *(type: string)* `[Optional]`
    - Adds or substracts this amount of weeks to the starting time until the ending time is reached.

    * **days** *(type: string)* `[Optional]`
    - Adds or substracts this amount of days to the starting time until the ending time is reached.

    * **hours** *(type: string)* `[Optional]`
    - Adds or substracts this amount of hours to the starting time until the ending time is reached.

    * **minutes** *(type: string)* `[Optional]`
    - Adds or substracts this amount of minutes to the starting time until the ending time is reached.

    * **seconds** *(type: string)* `[Optional]`
    - Adds or substracts this amount of seconds to the starting time until the ending time is reached.

