### tuktu.processors.json.JSONCreatorProcessor
Creates a JSON element and allows to insert evaluated Tuktu strings.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **json** *(type: any)* `[Required]`
    - The JSON element, can contain Tuktu Strings (in strings). If you want something to be parsed (for example Tuktu Strings), place them inside a string and %{...}, like for example { a: "%{${list}}" } -> { a: [ 1, 2, 3 ] } if list's string representation is "[1,2,3]".

