### tuktu.db.processors.DeleteProcessor
Deletes a bucket from the Tuktu DB

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **key** *(type: string)* `[Required]`
    - The key of the bucket to delete. Insert field values using ${..} notation.

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to wait for the deletion to have occured before continuing.

