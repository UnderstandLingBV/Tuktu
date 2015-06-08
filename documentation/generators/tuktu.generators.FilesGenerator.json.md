### tuktu.generators.FilesGenerator
Generate a stream of File objects from selected files and directories.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.
 
    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)
 
      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type
 
      * **instances** *(type: int)* `[Optional]`
      - The amount of instances per node of this handler type
 
  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **filesAndDirs** *(type: array)* `[Required]`
    - Files and directories to generate File objects from.
 
      * **fileOrDir** *(type: string)* `[Required]`
      - Path to file, or directory to generate File objects from.
 
    * **pathMatcher** *(type: string)* `[Optional]`
    - The path matcher to match all encountered files against: For example glob:**.json for all json files, glob:**.{json,txt} for all json and txt files, glob:**/folder/*.{json,txt} for all json and text files in a directory named 'folder'. Also supports regex:pattern.
 
    * **recursive** *(type: boolean)* `[Optional]`
    - If true, the directories will be searched recursively.
 
