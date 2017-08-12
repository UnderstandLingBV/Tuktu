### tuktu.dlib.processors.TemplateProcessor
Generates a text output based on a template and changing data (using the Apache FreeMarker template engine).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **template** *(type: string)* `[Required]`
    - The URL at which the FTL template to apply can be found.

    * **encodings** *(type: string)* `[Optional, default = "UTF-8"]`
    - The template encodings.

    * **data** *(type: string)* `[Required]`
    - The field containing the changing data used to fill in the template.

