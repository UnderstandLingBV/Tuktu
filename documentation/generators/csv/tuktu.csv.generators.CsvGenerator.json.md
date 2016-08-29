### tuktu.csv.generators.CSVGenerator
Parses a given CSV file with predefined or provided headers.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **filename** *(type: string)* `[Required]`
    - The name of the file to read from.

    * **encoding** *(type: string)* `[Optional, default = "utf-8"]`
    - The characterset encoding of the file.

    * **has_headers** *(type: boolean)* `[Optional, default = false]`
    - If set to true, the first row will be considered the headers, and will be used as value names.

    * **predef_headers** *(type: array)* `[Optional]`
    - Will be ignored if has_headers is true. Otherwise the list provided will be used in order as headers, ie. as value names for the columns.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **flattened** *(type: boolean)* `[Optional, default = false]`
    - Will be ignored if has_headers is false and no predef_headers are provided. Otherwise it will flatten the result given by those headers, or not by mapping resultName to that map.

    * **separator** *(type: string)* `[Optional, default = ";"]`
    - The separator character used in the given CSV file.

    * **quote** *(type: string)* `[Optional, default = "\""]`
    - The quote character used in the given CSV file.

    * **escape** *(type: string)* `[Optional, default = "\\"]`
    - The escape character used in the given CSV file.

    * **start_line** *(type: int)* `[Optional]`
    - The line number (starting with 0) to start reading from. From beginning if not entered. Does not include the header.

    * **end_line** *(type: int)* `[Optional]`
    - The last line number to read. Until the end of the file if not entered.

    * **batch_size** *(type: int)* `[Optional, default = 1000]`
    - While lines are processed one by one, the buffered reader takes them in batches. Tweak for best performance.

    * **batched** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to send the buffered lines all at once or line by line.

    * **ignore_error_lines** *(type: boolean)* `[Optional, default = false]`
    - If set to true, a line that cannot be parsed will be ignored. Note that this can be dangerous if the error spans more than one (CSV) line.

    * **backoff_interval** *(type: int)* `[Optional, default = 1000]`
    - After reading this many batches, we backoff for a while to make sure we don't flood the flow.

    * **backoff_amount** *(type: int)* `[Optional, default = 10]`
    - This is the amount of milliseconds to backoff for.

