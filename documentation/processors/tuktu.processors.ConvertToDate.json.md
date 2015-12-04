### tuktu.processors.ConvertToDate
Converts a String to a Java Date.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field containing the date.

    * **format** *(type: string)* `[Optional, default = "EEE MMM dd HH:mm:ss zzz yyyy"]`
    - The string formatter.

    * **locale** *(type: string)* `[Optional, default = "US"]`
    - The locale for the string formatter.

