### tuktu.processors.meta.GeneratorConfigProcessor
Invokes a new generator via config, which can be appended by data packets.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the config file, without extension (.json).

    * **add_fields** *(type: array)* `[Optional]`
    - Fields to add to the config.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **source** *(type: string)* `[Required]`
        - The field (key) whose value will be used as value for the config, with the key given below: target -> data(source)

        * **target** *(type: string)* `[Required]`
        - The key used for the config with the value from the data packet at the given field provided above: target -> data(source)

