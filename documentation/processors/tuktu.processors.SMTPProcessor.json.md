### tuktu.processors.SMTPProcessor
Sends an e-mail using SMTP for each datum in the DataPacket.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **server_name** *(type: string)* `[Required]`
    - The SMTP server name

    * **port** *(type: int)* `[Required]`
    - The port number to use

    * **username** *(type: string)* `[Required]`
    - The username to authenticate with

    * **password** *(type: string)* `[Required]`
    - The password to authenticate with

    * **tls** *(type: boolean)* `[Optional, default = true]`
    - Whether or not to use tls

    * **from** *(type: string)* `[Required]`
    - The from e-mail address

    * **to** *(type: string)* `[Required]`
    - The to e-mail address to use, split on comma for multiple

    * **cc** *(type: string)* `[Required]`
    - The CC e-mail address to use, split on comma for multiple

    * **bcc** *(type: string)* `[Required]`
    - The BCC e-mail address to use, split on comma for multiple

    * **subject** *(type: string)* `[Required]`
    - The subject to use

    * **body** *(type: string)* `[Required]`
    - The e-mail body

    * **content_type** *(type: string)* `[Optional, default = "html"]`
    - The e-mail content type, use 'text' for plain text and anything else for rich (HTML) content

    * **wait_for_sent** *(type: boolean)* `[Optional, default = false]`
    - Wait for the e-mails to be sent before going on

