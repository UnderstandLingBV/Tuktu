### tuktu.processors.sample.StratifiedSamplingProcessor
Takes stratified data from a DataPacket, equaling the class counts.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **class_field** *(type: string)* `[Required]`
    - The name of the field that contains the class values upon which stratified sampling should be based.

    * **random** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to take random strata. If set to false, the order inside the DataPacket will be maintained and the datums picked will be the first ones encountered.

