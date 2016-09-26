### tuktu.processors.meta.GeneratorWrapperProcessor
Wraps a generator and returns its result after EOF.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **generator_name** *(type: string)* `[Required]`
    - The name of the config generator.

    * **generator_config** *(type: JsObject)* `[Optional]`
    - The configuration for the generator.

    * **as_whole** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the entire generator's data is buffered and released upon termination of the generator. If set to false, the data resulting from the generator is directly streamed.

