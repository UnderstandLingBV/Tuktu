### tuktu.nosql.processors.HDFSWriterProcessor
Writes specific fields of the datapacket out to HDFS, by default as JSON.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **uri** *(type: string)* `[Required]`
    - Location of HDFS; e.g. hdfs://localhost:51234.
 
    * **file_name** *(type: string)* `[Required]`
    - Path of the file to write to.
 
    * **fields** *(type: array)* `[Required]`
    - All the fields that will be written to HDFS.
 
      * **[UNNAMED]** *(type: string)* `[Required]`

    * **field_separator** *(type: string)* `[Required]`
    - A separator for separating fields
 
    * **datapacket_separator** *(type: string)* `[Required]`
    - A separator for separating datapackets
 
    * **replication** *(type: int)* `[Optional]`
    - Replication factor of the file on HDFS.
 
