### tuktu.generators.XmlGenerator
Reads in an XML file and selects elements from it to stream further. Node that this generator reads the full XML into memory.

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

    * **file_name** *(type: string)* `[Required]`
    - The file name to read XML from.

    * **as_text** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to turn the content of the XML nodes into text.

    * **trim** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to trim the text if as_text is set to true.

    * **query** *(type: array)* `[Optional, default = true]`
    - The XPath-like query to fetch.

      * **selector** *(type: object)* `[Required]`
      - The XPath-like selector

        * **type** *(type: string)* `[Required]`
        - The type of selector, can be either \ for children, \\ for descendants or @ for attributes.

        * **string** *(type: string)* `[Required]`
        - The actual selector string.

