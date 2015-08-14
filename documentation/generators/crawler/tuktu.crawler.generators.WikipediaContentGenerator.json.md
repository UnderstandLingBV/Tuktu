### tuktu.crawler.generators.WikipediaContentGenerator
Crawls wikipedia's content for a specific language

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **include_word** *(type: boolean)* `[Optional, default = false]`
      - Whether or not to send the crawled word/page name as well

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **language** *(type: string)* `[Required]`
    - The language to crawl for (wikipedia abbreviation).

    * **seed_words** *(type: array)* `[Required]`
    - The seed words to start from.

      * **[UNNAMED]** *(type: string)* `[Required]`

    * **max_links** *(type: int)* `[Optional, default = 0]`
    - The maximum number of links to follow from a given page, useful for memory capping. If set to 0, all links are followed.

