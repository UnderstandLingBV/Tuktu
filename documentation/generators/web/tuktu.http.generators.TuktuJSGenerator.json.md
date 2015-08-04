### tuktu.http.generators.TuktuJSGenerator
Generates analytics JavaScript code.

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

    * **api_endpoint** *(type: string)* `[Required]`
    - The API endpoint to send analytics data to.

    * **includes** *(type: array)* `[Required]`
    - Rules for analytics inclusion.

      * **include** *(type: object)* `[Required]`
      - 

        * **page_regex** *(type: string)* `[Required]`
        - The regular expression the referrer has to contain to be served the following block of captures.

        * **captures** *(type: array)* `[Required]`
        - List of captures for the matched pages.

          * **capture** *(type: object)* `[Required]`
          - 

            * **identifier** *(type: string)* `[Optional]`
            - An identifier for this capture. Will default to the selector.

            * **xpath** *(type: boolean)* `[Optional, default = false]`
            - Use XPath selection if true, or CSS selection otherwise.

            * **selector** *(type: string)* `[Required]`
            - XPath or CSS selector, depending on XPath value above.

            * **event** *(type: string)* `[Required]`
            - The event type to capture.

