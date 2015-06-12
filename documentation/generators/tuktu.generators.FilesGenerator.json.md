### tuktu.generators.FilesGenerator
Generate a stream of Path objects from selected files and directories.

  * **nodes** *(type: array)* `[Optional]`
  - Optionally specify on which nodes to run and how many instances you want on each node.

    * **[UNNAMED]** *(type: object)* `[Required]`

      * **type** *(type: string)* `[Required]`
      - The type of node handler, one of SingleNode, SomeNodes, AllNodes (leave empty for local execution)

      * **nodes** *(type: string)* `[Required]`
      - The nodes to use for this node handler type

      * **instances** *(type: int)* `[Optional, default = 1]`
      - The amount of instances per node of this handler type

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **filesAndDirs** *(type: array)* `[Required]`
    - Files and directories to generate Path objects from.

      * **fileOrDir** *(type: string)* `[Required]`
      - Path to file, or directory to generate Path objects from.

    * **pathMatcher** *(type: string)* `[Optional, default = "glob:**"]`
    - The path matcher to match against the path of all encountered files: For example glob:**.json for all json files, glob:**.{json,txt} for all json and txt files, glob:**/folder/*.{json,txt} for all json and text files in a directory named 'folder'. Also supports regex:pattern.

    * **recursive** *(type: boolean)* `[Optional, default = false]`
    - If true, the directories will be searched recursively.

