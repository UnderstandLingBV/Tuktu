### tuktu.social.processors.FacebookTaggerProcessor
Returns a subset of provided keywords that are found in a message, and a subset of provided users that are author or recipient of the message.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **object_field** *(type: string)* `[Required]`
    - The name of the field in which the Facebook object is.

    * **tags** *(type: object)* `[Required]`

      * **keywords** *(type: array)* `[Required]`
      - The keywords to be filtered for what can be found in the message.

        * **[UNNAMED]** *(type: string)* `[Required]`

      * **users** *(type: array)* `[Required]`
      - The users to be filtered for who is relevant to a message, that is the author and the recipient.

        * **[UNNAMED]** *(type: string)* `[Required]`

    * **exclude_on_none** *(type: boolean)* `[Required]`
    - Exclude messages which do not match any filter.

