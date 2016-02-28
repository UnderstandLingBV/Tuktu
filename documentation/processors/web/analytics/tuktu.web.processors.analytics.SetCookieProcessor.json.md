### tuktu.web.processors.analytics.SetCookieProcessor
Sets a cookie (resultName will be the name of the cookie).

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **value** *(type: string)* `[Required]`
    - The cookie's value.

    * **expires** *(type: string)* `[Optional]`
    - The expiration date time as string.

    * **path** *(type: string)* `[Optional]`
    - The cookie's path.

    * **only_if_not_exists** *(type: boolean)* `[Optional, default = true]`
    - If set to true, this cookie will only be set if it doesn't exist already, otherwise it will be overwritten if it exists.

