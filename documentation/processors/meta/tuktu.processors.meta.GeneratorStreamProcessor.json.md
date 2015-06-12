### tuktu.processors.meta.GeneratorStreamProcessor
Invokes a new generator, which sends its data to a given list of processors.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the config file.

    * **node** *(type: string)* `[Optional]`
    - A Tuktu SingleNode to execute on.

    * **next** *(type: array)* `[Required]`
    - The processors to send data into.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **processors** *(type: array)* `[Required]`
    - The actual config, being a list of processors.

      * **[UNNAMED]** *(type: JsObject)* `[Required]`

    * **sync** *(type: boolean)* `[Optional, default = false]`

