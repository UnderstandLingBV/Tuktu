### tuktu.processors.meta.ParallelProcessor
No description present.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **processors** *(type: array)* `[Required]`

      * **[UNNAMED]** *(type: object)* `[Required]`

        * **start** *(type: string)* `[Required]`

        * **pipeline** *(type: array)* `[Required]`

          * **[UNNAMED]** *(type: object)* `[Required]`

            * **id** *(type: string)* `[Required]`

            * **name** *(type: string)* `[Required]`

            * **config** *(type: any)* `[Required]`

            * **result** *(type: string)* `[Required]`

            * **next** *(type: array)* `[Required]`

              * **[UNNAMED]** *(type: string)* `[Required]`

    * **merger** *(type: string)* `[Required]`

