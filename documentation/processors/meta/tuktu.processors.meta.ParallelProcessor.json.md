### tuktu.processors.meta.ParallelProcessor
Starts parallel processor pipelines, which are merge upon completion.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **merger** *(type: string)* `[Required]`
    - The merger class to be used to merge the results of the processors.
 
    * **processors** *(type: array)* `[Required]`
    - Each entry is defining a pipeline of processors for which an Enumeratee is built. The results of each is then merged using the merger class.
 
      * **[UNNAMED]** *(type: object)* `[Required]`

        * **start** *(type: string)* `[Required]`
        - The ID of the processor to compose first.
 
        * **pipeline** *(type: array)* `[Required]`
        - The actual pipeline of processors.
 
          * **[UNNAMED]** *(type: object)* `[Required]`

            * **id** *(type: string)* `[Required]`
            - The Id of the processor.
 
            * **name** *(type: string)* `[Required]`
            - The name of the processor.
 
            * **config** *(type: JsObject)* `[Required]`
            - The config of the processor.
 
            * **result** *(type: string)* `[Required]`
            - The result of the processor.
 
            * **next** *(type: array)* `[Required]`
            - The next processors to be composed.
 
              * **[UNNAMED]** *(type: string)* `[Required]`

