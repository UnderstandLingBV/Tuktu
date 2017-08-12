### tuktu.processors.SignalBufferProcessor
Hybrid processor that either buffers data until a signal is received, or sends the signal. This means that you MUST always have 2 instances of this processor active, in separate branches. WARNING: IMPROPER USE OF THIS PROCESSOR WILL LEAD TO DEADLOCK!

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **signal_name** *(type: string)* `[Required]`
    - The unique name of the signal. This should match for signaller and signalee.

    * **is_signaller** *(type: boolean)* `[Required]`
    - Whether or not this instance is a signaller. WARNING: There must always be a signaller AND a signalee!

    * **node** *(type: string)* `[Optional, default = ""]`
    - The name of the node the signallee lives on. If set, the signal can be sent to a remote note from the signaller to the signalee.

    * **sync** *(type: boolean)* `[Optional, default = false]`
    - Whether or not the result of the remaining flow is required to be send back.

