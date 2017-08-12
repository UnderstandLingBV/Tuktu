### tuktu.social.processors.TwitterTaggerProcessor
Returns a subset of provided keywords that are found in a tweet, and a subset of provided users that are relevant to the tweet.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **object_field** *(type: string)* `[Required]`
    - The name of the field in which the Twitter object is.

    * **tags** *(type: object)* `[Required]`

      * **keywords** *(type: array)* `[Optional]`
      - The keywords to be filtered for what can be found in the tweet.

        * **[UNNAMED]** *(type: object)* `[Required]`

          * **keyword** *(type: string)* `[Required]`
          - The keyword to search for in a tweet.

          * **exact** *(type: boolean)* `[Optional, default = false]`
          - If this keyword contains multiple words (space-separated), a non-exact match will look for all words to be present, but it any given order.

          * **case_sensitive** *(type: boolean)* `[Optional, default = false]`
          - Whether or not the matching should be case sensitive.

      * **users** *(type: array)* `[Optional]`
      - The users to be filtered for who is relevant to a tweet, for example as author, as mention, as retweeter, or as reply to that user.

        * **[UNNAMED]** *(type: string)* `[Required]`

    * **user_tag_field** *(type: string)* `[Optional, default = ""]`
    - Define which field is used to retrieve user tags from. Usually it is either 'name', 'screen_name', 'id', or left empty. Leave empty to get at most those strings matching the above users list, which could be a mix of names and ids. Otherwise, get the value of this user tag field of those matching users. For example one could match them by id (put the ids in the above list), and then tag them by name (put 'name' or 'screen_name' here).

    * **exclude_on_none** *(type: boolean)* `[Optional, default = false]`
    - Exclude tweets which do not match any filter.

    * **combined** *(type: boolean)* `[Optional, default = false]`
    - Combine the tags? If set to false, a map with keys keywords and users is added with all tags. If set to true, a list is returned.

