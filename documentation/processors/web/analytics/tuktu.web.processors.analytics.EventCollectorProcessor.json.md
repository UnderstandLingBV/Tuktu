### tuktu.web.processors.analytics.EventCollectorProcessor
Adds an event listener to a specific DOM element and sends collected data to Tuktu when the event is triggered. The event that was triggered will add a variable with resultName set to true when submitting to Tuktu.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **selector** *(type: string)* `[Required]`
    - The query selector.

    * **event_name** *(type: string)* `[Required]`
    - The event name that should be listened to.

    * **event_value** *(type: string)* `[Optional, default = "true"]`
    - The value that should be assigned to the event (ie. 'true').

    * **flow_name** *(type: string)* `[Required]`
    - The name of the flow where the data triggered by this event should be sent to.

