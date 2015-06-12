### tuktu.social.generators.LinkedinGenerator
This generator is not fully implemented yet.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.
 
    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)
 
      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type
 
      * **instances** *(type: int)* `[Optional]`
      - The amount of instances per node of this handler type
 
  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **credentials** *(type: object)* `[Required]`

      * **consumer_key** *(type: string)* `[Required]`
      - The ID for the application.
 
      * **consumer_secret** *(type: string)* `[Required]`
      - The secret for the application.
 
      * **access_token** *(type: string)* `[Required]`
      - The access token for the user.
 
      * **access_token_secret** *(type: string)* `[Required]`
      - The access token secret for the user.
 
    * **url** *(type: string)* `[Required]`
    - The URL of the end-point for the API.
 
    * **http_method** *(type: string)* `[Optional]`
    - Usually one of get, post, put, delete.
 
    * **field** *(type: string)* `[Required]`
    - JSON field to extract from response.
 
