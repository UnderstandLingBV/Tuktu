### tuktu.processors.statistics.NumberWithProbabilityProcessor
Assigns a number to each datapacket's datum with a specific probability

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **numbers** *(type: array)* `[Required]`
    - The numbers with probabilities.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **number** *(type: int)* `[Required]`
        - The number that should be assigned

        * **probability** *(type: double)* `[Required]`
        - The probability with which this number should be assigned.

