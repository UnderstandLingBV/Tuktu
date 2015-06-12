### tuktu.nosql.processors.CassandraProcessor
Executes a query on a given Cassandra node, optionally appending the result to the data packet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **address** *(type: string)* `[Required]`
    - The address of the node to connect to; optionally appended by :port, otherwise port 9042 will be assumed.
 
    * **query** *(type: string)* `[Required]`
    - The query to be run.
 
    * **append** *(type: boolean)* `[Optional]`
    - Append the result to the data packet; otherwise the result is ignored.
 
