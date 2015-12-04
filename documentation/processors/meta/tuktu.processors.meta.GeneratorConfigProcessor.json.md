### tuktu.processors.meta.GeneratorConfigProcessor
Invokes a new generator via config, which can be appended by data packets.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **name** *(type: string)* `[Required]`
    - The name of the config file, without extension (.json).

    * **replacements** *(type: array)* `[Optional]`
    - The replacements used to replace Tuktu config strings #{source} by target.

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **source** *(type: string)* `[Required]`
        - The #{key} that will be replaced by the target string below in the invoked configs: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

        * **target** *(type: string)* `[Required]`
        - The replacement for the source above: #{source} -> target. Can contain Tuktu strings to populate with first Datum.

