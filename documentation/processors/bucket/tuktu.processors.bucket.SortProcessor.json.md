### tuktu.processors.bucket.SortProcessor
Sorts elements in a bucket by a given field.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **field** *(type: string)* `[Required]`
    - The field to sort on.

    * **asc_desc** *(type: string)* `[Optional, default = "asc"]`
    - Whether to sort ascending (asc) or descending (desc). Any value other than 'desc' will be assumed ascending.

