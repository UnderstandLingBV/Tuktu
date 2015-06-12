### tuktu.social.processors.TwitterTaggerProcessor
Returns a subset of provided keywords that are found in a tweet, and a subset of provided users that are relevant to the tweet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **object_field** *(type: string)* `[Required]`
    - The name of the field in which the Twitter object is.
 
    * **tags** *(type: object)* `[Required]`

      * **keywords** *(type: array)* `[Required]`
      - The keywords to be filtered for what can be found in the tweet.
 
        * **[UNNAMED]** *(type: string)* `[Required]`

      * **users** *(type: array)* `[Required]`
      - The users to be filtered for who is relevant to a tweet, for example as author, as mention, as retweeter, or as reply to that user.
 
        * **[UNNAMED]** *(type: string)* `[Required]`

      * **geos** *(type: array)* `[Optional]`
      - To be implemented...
 
        * **[UNNAMED]** *(type: string)* `[Required]`

    * **exclude_on_none** *(type: boolean)* `[Optional]`
    - Exclude tweets which do not match any filter.
 
