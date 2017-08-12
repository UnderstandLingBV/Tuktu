### tuktu.crawler.generators.GenericCrawlerGenerator
Crawls a webpage generically for content

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

  * **result** *(type: string)* `[Required]`

  * **stop_on_error** *(type: boolean)* `[Optional, default = true]`
  - If set to false, Tuktu will not kill the flow on data error.

  * **config** *(type: object)* `[Required]`

    * **url** *(type: string)* `[Required]`
    - The starting URL.

    * **link_pattern** *(type: string)* `[Required]`
    - The XPath pattern to follow links through.

    * **crawl_pattern** *(type: string)* `[Required]`
    - The XPath pattern defining which elements to fetch.

    * **flatten** *(type: boolean)* `[Optional, default = false]`
    - Whether or not to send results from one page in one datum or one datum per result.

