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

  * **config** *(type: object)* `[Required]`

    * **filename** *(type: string)* `[Required]`
    - The name of the file to read from.

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

