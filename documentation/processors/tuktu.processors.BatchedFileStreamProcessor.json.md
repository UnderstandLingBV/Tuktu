### tuktu.processors.BatchedFileStreamProcessor
Streams data into a file and closes it when it's done.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **file_name** *(type: string)* `[Required]`
    - The location of the file to write to.
 
    * **encoding** *(type: string)* `[Optional]`
    - The encoding used to write to the file.
 
    * **field_separator** *(type: string)* `[Optional]`
    - Field separator character.
 
    * **line_separator** *(type: string)* `[Optional]`
    - Line separator character, default is 
.
 
    * **batch_size** *(type: int)* `[Required]`
    - The size of the batch (buffer) to be written into the file.
 
    * **fields** *(type: array)* `[Required]`
    - The fields to be written out.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

