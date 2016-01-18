### tuktu.dlib.processors.MetadataSerializationProcessor
Serializes a metadata record to a file.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **folder** *(type: string)* `[Required]`
    - The folder in which the metadata record will be saved.

    * **fileName** *(type: string)* `[Required]`
    - The name of the metadata record file to create

    * **prefix** *(type: string)* `[Optional]`
    - A header to prefix to the metadata record (e.g., <?xml version="1.0" encoding="UTF-8"?>).

    * **content** *(type: string)* `[Required]`
    - The main metadata content of the record.

    * **postfix** *(type: string)* `[Optional]`
    - A footer to postfix to the metadata record.

    * **encoding** *(type: string)* `[Required]`
    - The file encoding

