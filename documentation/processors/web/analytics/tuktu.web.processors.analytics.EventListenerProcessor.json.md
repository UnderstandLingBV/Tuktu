### tuktu.web.processors.analytics.EventListenenerProcessor
Adds an event listener to the JS.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **element_id** *(type: string)* `[Required]`
    - The ID of the DOM element.

    * **event_name** *(type: string)* `[Required]`
    - The event name that should be listened to.

    * **callback** *(type: string)* `[Optional]`
    - The actual code to execute. If nothing is entered, a boolean value of true will be added to the collection.

