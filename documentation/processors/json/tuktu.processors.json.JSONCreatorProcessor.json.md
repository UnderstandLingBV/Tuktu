### tuktu.processors.json.JSONCreatorProcessor
Creates a JSON element and allows to insert evaluated Tuktu strings.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **json** *(type: any)* `[Required]`
    - The JSON element, can contain Tuktu Strings (in JsStrings). If you want to insert a JsObject using Tuktu Strings, you can do: $JSON.parse{${jsObject}}, or if you want to convert a Map to a JsObject on the fly: $JSON.parse{$JSON.stringify{map}}. Works not only on top-level, but also as a value within an object as JsString, and not only on maps, for example: {"${key}": "$JSON.parse{$JSON.stringify{list}}"} (Remember that keys never need to and hence can not be parsed as JSON, since they need to be strings.)

