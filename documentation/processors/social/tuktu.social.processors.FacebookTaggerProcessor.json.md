### tuktu.social.processors.FacebookTaggerProcessor
Returns a subset of provided keywords that are found in a message, and a subset of provided users that are author or recipient of the message.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **object_field** *(type: string)* `[Required]`
    - The name of the field in which the Facebook object is.

    * **tags** *(type: object)* `[Required]`

      * **users** *(type: array)* `[Optional]`
      - The users to be filtered for who is relevant to a message, that is the author and the recipient.

        * **[UNNAMED]** *(type: string)* `[Required]`

    * **user_tag_field** *(type: string)* `[Optional, default = ""]`
    - Define which field is used to retrieve user tags from. Usually it is either 'name', or 'id', or left empty. Leave empty to get at most those strings matching the above users list, which could be a mix of names and ids. Otherwise, get the value of this user tag field of those matching users. For example one could match them by id (put the ids in the above list), and then tag them by name (put 'name' here).

    * **exclude_on_none** *(type: boolean)* `[Required]`
    - Exclude messages which do not match any filter.

    * **combined** *(type: boolean)* `[Optional, default = false]`
    - Combine the tags? If set to false, a map with keys keywords and users is added with all tags. If set to true, a list is returned.

    * **user_replacements** *(type: array)* `[Optional]`
    - You can replace tags (which are best set as Facebook IDs) with other values (like a pre-known user name).

      * **replacement** *(type: object)* `[Required]`

        * **source** *(type: string)* `[Required]`
        - The source value for a replacement (most likely the Facebook ID).

        * **target** *(type: string)* `[Required]`
        - The target value for a replacement (most likely a human readable name/username).

