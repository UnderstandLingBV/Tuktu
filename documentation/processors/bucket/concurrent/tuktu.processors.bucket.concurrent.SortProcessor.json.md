### tuktu.processors.bucket.concurrent.SortProcessor
Sorts items in a bucket in a distributed fashion based on some field.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **nodes** *(type: array)* `[Required]`
    - The (Tuktu) nodes to run the sorting on.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field (key) to sort items on.

    * **asc_desc** *(type: string)* `[Optional, default = "asc"]`
    - Whether to sort ascending (asc) or descending (desc). Any value other than 'desc' will be assumed ascending.

