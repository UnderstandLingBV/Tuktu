### tuktu.processors.RunningCountProcessor
Adds a running count integer to the data packets coming in.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **start_at** *(type: int)* `[Optional]`
    - The integer to start with.
 
    * **per_block** *(type: boolean)* `[Optional]`
    - Increase the integer for each data packet, or for each element in each data packet.
 
    * **step_size** *(type: int)* `[Optional]`
    - The step in which the integer is increased with each data packet.
 
